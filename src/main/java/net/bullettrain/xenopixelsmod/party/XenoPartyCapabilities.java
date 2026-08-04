package net.bullettrain.xenopixelsmod.party;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Capability registration and event handlers for party system.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoPartyCapabilities {
    public static final Capability<XenoPartyData> PARTY_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = new ResourceLocation(XenoPixelsMod.MOD_ID, "party_data");

    private XenoPartyCapabilities() {}

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new XenoPartyDataProvider());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PARTY_DATA).ifPresent(oldData ->
                event.getEntity().getCapability(PARTY_DATA).ifPresent(newData -> newData.copyFrom(oldData)));
        event.getOriginal().invalidateCaps();
    }
}
