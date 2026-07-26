package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, XenoPixelsMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<MissileChunkLoaderBlockEntity>> MISSILE_CHUNK_LOADER =
            BLOCK_ENTITIES.register("missile_chunk_loader", () ->
                    BlockEntityType.Builder.of(MissileChunkLoaderBlockEntity::new,
                            ModBlocks.MISSILE_CHUNK_LOADER.get()).build(null));

    public static final RegistryObject<BlockEntityType<ShipVlsGuidanceBlockEntity>> SHIP_VLS_GUIDANCE =
            BLOCK_ENTITIES.register("ship_vls_guidance", () ->
                    BlockEntityType.Builder.of(ShipVlsGuidanceBlockEntity::new,
                            ModBlocks.SHIP_VLS_GUIDANCE.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
