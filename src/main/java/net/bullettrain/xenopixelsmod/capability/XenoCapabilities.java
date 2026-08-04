package net.bullettrain.xenopixelsmod.capability;

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

@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoCapabilities {
    public static final Capability<XenoPlayerData> XENO_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<XenoPartyData> XENO_PARTY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = new ResourceLocation(XenoPixelsMod.MOD_ID, "xeno_data");
    public static final ResourceLocation PARTY_KEY = new ResourceLocation(XenoPixelsMod.MOD_ID, "xeno_party");

    private XenoCapabilities() {}

    // XenoPlayerData and XenoPartyData are registered via @AutoRegisterCapability

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new XenoDataProvider());
            event.addCapability(PARTY_KEY, new XenoPartyProvider());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(XENO_DATA).ifPresent(oldData ->
                event.getEntity().getCapability(XENO_DATA).ifPresent(newData -> newData.copyFrom(oldData)));
        event.getOriginal().getCapability(XENO_PARTY).ifPresent(oldParty ->
                event.getEntity().getCapability(XENO_PARTY).ifPresent(newParty -> newParty.copyFrom(oldParty)));
        event.getOriginal().invalidateCaps();
    }
}
