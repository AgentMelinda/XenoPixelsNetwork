package net.bullettrain.xenopixelsmod.aero.power;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.block.entity.CopycatGlowstoneBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes the flight controller's FE buffer to adjacent machines.
 *
 * <p>Registered on the <b>mod</b> event bus — {@link RegisterCapabilitiesEvent} does not fire
 * on the game bus. The storage is receive-only (see {@link AeroEnergyStorage}), so this makes
 * the controller a valid power sink for Mekanism, Create-adjacent generators, or anything else
 * speaking Forge Energy, without letting them pull the reserve back out.
 *
 * <p>No Mekanism-specific dependency is needed; it exposes standard FE.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AeroCapabilities {

    private AeroCapabilities() {
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Context type is Direction: energy is exposed on every face, so the side is ignored.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SHIP_VLS_GUIDANCE.get(),
                (ShipVlsGuidanceBlockEntity be, Direction side) -> be.aeroEnergy());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.COPYCAT_GLOWSTONE.get(),
                (CopycatGlowstoneBlockEntity be, Direction side) -> be.energy());
        // A seat flying a small craft on its own draws power exactly as a controller does, so it
        // needs to be a valid FE sink too.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.PILOT_SEAT.get(),
                (net.bullettrain.xenopixelsmod.block.entity.PilotSeatBlockEntity be, Direction side)
                        -> be.aeroEnergy());
    }
}
