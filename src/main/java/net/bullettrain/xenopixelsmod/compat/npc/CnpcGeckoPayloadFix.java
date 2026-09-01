package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * CNPC-Gecko-Addon never registers its animation payloads: {@code NetworkWrapper} is
 * {@code @EventBusSubscriber(modid="customnpcs")} and its Type ids are in the
 * {@code minecraft:} namespace, which NeoForge rejects. We register namespaced ids
 * and mixin {@code type()} to match.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CnpcGeckoPayloadFix {
    private CnpcGeckoPayloadFix() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        if (!ModList.get().isLoaded("cnpcgeckoaddon")) {
            return;
        }
        try {
            CnpcGeckoPayloads.register(event);
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("CNPC-Gecko-Addon payload registration failed: {}", t.toString());
        }
    }
}
