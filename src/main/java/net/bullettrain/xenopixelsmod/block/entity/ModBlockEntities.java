package net.bullettrain.xenopixelsmod.block.entity;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileChunkLoaderBlockEntity>> MISSILE_CHUNK_LOADER =
            BLOCK_ENTITIES.register("missile_chunk_loader", () ->
                    BlockEntityType.Builder.of(MissileChunkLoaderBlockEntity::new,
                            ModBlocks.MISSILE_CHUNK_LOADER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopycatGlowstoneBlockEntity>> COPYCAT_GLOWSTONE =
            BLOCK_ENTITIES.register("copycat_glowstone", () ->
                    BlockEntityType.Builder.of(CopycatGlowstoneBlockEntity::new,
                            ModBlocks.COPYCAT_GLOWSTONE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipVlsGuidanceBlockEntity>> SHIP_VLS_GUIDANCE =
            BLOCK_ENTITIES.register("ship_vls_guidance", () ->
                    BlockEntityType.Builder.of(ShipVlsGuidanceBlockEntity::new,
                            ModBlocks.SHIP_VLS_GUIDANCE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipThrusterBlockEntity>> SHIP_THRUSTER =
            BLOCK_ENTITIES.register("ship_thruster", () ->
                    BlockEntityType.Builder.of(ShipThrusterBlockEntity::new,
                            ModBlocks.SHIP_THRUSTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileTubeBlockEntity>> MISSILE_TUBE =
            BLOCK_ENTITIES.register("missile_tube", () ->
                    BlockEntityType.Builder.of(MissileTubeBlockEntity::new,
                            ModBlocks.MISSILE_TUBE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PilotSeatBlockEntity>> PILOT_SEAT =
            BLOCK_ENTITIES.register("pilot_seat", () ->
                    BlockEntityType.Builder.of(PilotSeatBlockEntity::new,
                            ModBlocks.PILOT_SEAT.get()).build(null));

    /** Shared by {@code WING_PANEL} and its two fixed-orientation subclasses
     * ({@code WingFlapHorizontalBlock}/{@code WingFlapVerticalBlock}) — same block entity, same
     * renderer (which matches on {@code instanceof WingPanelBlock}, true for all three), only
     * where each is allowed to mount differs. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WingPanelBlockEntity>> WING_PANEL =
            BLOCK_ENTITIES.register("wing_panel", () ->
                    BlockEntityType.Builder.of(WingPanelBlockEntity::new,
                            ModBlocks.WING_PANEL.get(),
                            ModBlocks.WING_FLAP_HORIZONTAL.get(),
                            ModBlocks.WING_FLAP_VERTICAL.get(),
                            ModBlocks.COPYCAT_WING_PANEL.get(),
                            ModBlocks.COPYCAT_WING_PANEL_LIT.get(),
                            ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL.get(),
                            ModBlocks.COPYCAT_WING_FLAP_HORIZONTAL_LIT.get(),
                            ModBlocks.COPYCAT_WING_FLAP_VERTICAL.get(),
                            ModBlocks.COPYCAT_WING_FLAP_VERTICAL_LIT.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
