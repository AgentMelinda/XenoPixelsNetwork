package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.block.entity.CopycatGlowstoneBlockEntity;
import net.bullettrain.xenopixelsmod.client.model.CopycatGlowstoneModel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BALLISTIC_MISSILE.get(), BallisticMissileRenderer::new);
        // GeckoLib rig for the flight controller. The vanilla block model still provides the
        // item form and the collision shape; this only replaces the in-world render.
        event.registerBlockEntityRenderer(
                net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities.SHIP_VLS_GUIDANCE.get(),
                context -> new net.bullettrain.xenopixelsmod.client.aero.FlightControllerRenderer());
    }

    /**
     * Wrap <b>every</b> blockstate variant, not just the default one.
     *
     * <p>The block carries {@code POWERED}, and with Forge Energy disabled it sits at
     * {@code powered=true} from its first server tick onwards. Wrapping only
     * {@code defaultBlockState()} therefore left the variant players actually see unwrapped,
     * so the copied material never rendered in the default configuration.
     */
    @SubscribeEvent
    public static void replaceCopycatModel(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (var state : ModBlocks.COPYCAT_GLOWSTONE.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (ignored, bakedModel) -> bakedModel instanceof CopycatGlowstoneModel
                            ? bakedModel : new CopycatGlowstoneModel(bakedModel));
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof CopycatGlowstoneBlockEntity copycat) {
                if (copycat.getMaterial().is(ModBlocks.COPYCAT_GLOWSTONE.get())) {
                    return 0xFFFFFF;
                }
                return event.getBlockColors().getColor(copycat.getMaterial(), level, pos, tintIndex);
            }
            return 0xFFFFFF;
        }, ModBlocks.COPYCAT_GLOWSTONE.get());
    }
}
