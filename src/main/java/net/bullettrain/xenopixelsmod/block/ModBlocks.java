package net.bullettrain.xenopixelsmod.block;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.custom.MissileChunkLoaderBlock;
import net.bullettrain.xenopixelsmod.block.custom.ShipVlsGuidanceBlock;
import net.bullettrain.xenopixelsmod.block.custom.SoundBlock;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;


public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, XenoPixelsMod.MOD_ID);


public static final RegistryObject<Block> SOUND_BLOCK = registerBlock("sound_block",
        () -> new SoundBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));

    /** Ship VLS: force-loads chunks for guided missiles. */
    public static final RegistryObject<Block> MISSILE_CHUNK_LOADER = registerBlock("missile_chunk_loader",
            () -> new MissileChunkLoaderBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(4.0F, 8.0F).requiresCorrectToolForDrops()));

    /** Ship VLS guidance computer — aim + pulse-launch nearby Ballistix VLS. */
    public static final RegistryObject<Block> SHIP_VLS_GUIDANCE = registerBlock("ship_vls_guidance",
            () -> new ShipVlsGuidanceBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 8.0F).requiresCorrectToolForDrops()));

 public static final RegistryObject<Block> JACKIETONITE_ORE_BLOCK = registerBlock("jackietonite_ore_block",
        () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 3.0F)
        )
);

 public static final RegistryObject<Block> RAW_JACKIETONITE_ORE_BLOCK = registerBlock("raw_jackietonite_ore_block",
        () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3.0F, 3.0F)
        )
 );

 public static final RegistryObject<Block> JACKIETONITE_ORE = registerBlock("jackietonite_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(3f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));
 public static final RegistryObject<Block> DEEPSLATE_JACKIETONITE_ORE = registerBlock("deepslate_jackietonite_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(3f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));
 public static final RegistryObject<Block> NETHER_JACKIETONITE_ORE = registerBlock("nether_jackietonite_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(1f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));
 public static final RegistryObject<Block> END_STONE_JACKIETONITE_ORE = registerBlock("end_stone_jackietonite_ore", () -> new DropExperienceBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(5f).requiresCorrectToolForDrops(), UniformInt.of(3, 6)));

    /*
    public static final RegistryObject<Block> JACKIETONITE_ORE_BLOCK = registerBlock("jackietonite_ore_block",
        () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
*/

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ModsItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
