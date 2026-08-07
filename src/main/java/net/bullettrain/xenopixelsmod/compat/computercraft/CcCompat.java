package net.bullettrain.xenopixelsmod.compat.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.ModList;

/**
 * Optional ComputerCraft: Tweaked integration.
 * Loaded reflectively from common setup so missing CC never crashes classloading of core code.
 */
public final class CcCompat {
    private CcCompat() {}

    public static void register() {
        if (!ModList.get().isLoaded("computercraft")) {
            XenoPixelsMod.LOGGER.info("ComputerCraft not present — thruster/VLS CC methods skipped");
            return;
        }
        try {
            ComputerCraftAPI.registerGenericSource(new ShipThrusterPeripheral());
            ComputerCraftAPI.registerGenericSource(new VlsGuidancePeripheral());
            XenoPixelsMod.LOGGER.info("Registered CC:Tweaked generic peripherals (thruster + VLS guidance)");
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("Failed to register CC peripherals: {}", t.toString());
        }
    }
}
