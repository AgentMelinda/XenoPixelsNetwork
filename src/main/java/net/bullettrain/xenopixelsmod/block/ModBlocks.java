package net.bullettrain.xenopixelsmod.block;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.custom.MissileChunkLoaderBlock;
import net.bullettrain.xenopixelsmod.block.custom.CopycatGlowstoneBlock;
import net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock;
import net.bullettrain.xenopixelsmod.block.custom.PilotSeatBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingPanelBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingFlapHorizontalBlock;
import net.bullettrain.xenopixelsmod.block.custom.WingFlapVerticalBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipThrusterBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock;
import net.bullettrain.xenopixelsmod.block.custom.SoundBlock;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<Block, Block> SOUND_BLOCK = registerBlock("sound_block",
            () -> new SoundBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    /** Maximum-light decorative block that renders a material applied with a block item. */
    public static final DeferredHolder<Block, CopycatGlowstoneBlock> COPYCAT_GLOWSTONE =
            registerBlock("copycat_glowstone", () -> new CopycatGlowstoneBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GLOWSTONE).lightLevel(state ->
                            state.getValue(CopycatGlowstoneBlock.POWERED) ? 15 : 0)));

    public static final DeferredHolder<Block, Block> JACKIETONITE_ORE_BLOCK = registerBlock("jackietonite_ore_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F)
            )
    );

    public static final DeferredHolder<Block, Block> RAW_JACKIETONITE_ORE_BLOCK = registerBlock("raw_jackietonite_ore_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.0F, 3.0F)
            )
    );

    public static final DeferredHolder<Block, Block> JACKIETONITE_ORE = registerBlock("jackietonite_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 6), BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                            .strength(2f)
                            .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, Block> DEEPSLATE_JACKIETONITE_ORE = registerBlock("deepslate_jackietonite_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 6), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)
                            .strength(3f)
                            .requiresCorrectToolForDrops()));

    /** Chunk-force along ballistic missile corridors (native Xeno missiles). */
    public static final DeferredHolder<Block, Block> MISSILE_CHUNK_LOADER = registerBlock("missile_chunk_loader",
            () -> new MissileChunkLoaderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /**
     * Ballistic Guidance Computer — world aim point + loft solve + fire nearby tubes.
     * Native XenoPixels system (not Ballistix).
     */
    public static final DeferredHolder<Block, Block> SHIP_VLS_GUIDANCE = registerBlock("ship_vls_guidance",
            () -> new ShipVlsGuidanceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5f, 8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Launch tube for {@link net.bullettrain.xenopixelsmod.missile.BallisticMissileEntity}. */
    public static final DeferredHolder<Block, Block> MISSILE_TUBE = registerBlock("missile_tube",
            () -> new MissileTubeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(4.0f, 10f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** VS2 directional thruster (Create-style plume + CC power control). */
    public static final DeferredHolder<Block, Block> SHIP_THRUSTER = registerBlock("ship_thruster",
            () -> new ShipThrusterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.5f, 8f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(ShipThrusterBlock.POWERED) ? 10 : 0)));

    public static final DeferredHolder<Block, Block> NETHER_JACKIETONITE_ORE = registerBlock("nether_jackietonite_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 6), BlockBehaviour.Properties.ofFullCopy(Blocks.NETHERRACK)
                            .strength(1f)
                            .requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, Block> END_STONE_JACKIETONITE_ORE = registerBlock("end_stone_jackietonite_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 6), BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE)
                            .strength(5f)
                            .requiresCorrectToolForDrops()));

    /** Sit here to fly the nearest flight controller. */
    public static final DeferredHolder<Block, PilotSeatBlock> PILOT_SEAT = registerBlock("pilot_seat",
            () -> new PilotSeatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(2.5f, 6f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Aerodynamic surface: generates lift where it is placed, via Sable's lift providers. */
    public static final DeferredHolder<Block, WingPanelBlock> WING_PANEL = registerBlock("wing_panel",
            () -> new WingPanelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Always mounts flat (AXIS=Y) — reach for this to build a flap, elevator or aileron that is
     * guaranteed to tilt up/down, matching Warium's own separate horizontal control surface. */
    public static final DeferredHolder<Block, WingFlapHorizontalBlock> WING_FLAP_HORIZONTAL =
            registerBlock("wing_flap_horizontal", () -> new WingFlapHorizontalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Always mounts vertically (AXIS=Z) — reach for this to build a rudder, matching Warium's
     * own separate vertical control surface. */
    public static final DeferredHolder<Block, WingFlapVerticalBlock> WING_FLAP_VERTICAL =
            registerBlock("wing_flap_vertical", () -> new WingFlapVerticalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    private static <T extends Block> DeferredHolder<Block, T> registerBlock(String name, Supplier<T> block) {
        DeferredHolder<Block, T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> DeferredHolder<Item, Item> registerBlockItem(String name, DeferredHolder<Block, T> block) {
        return ModsItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
