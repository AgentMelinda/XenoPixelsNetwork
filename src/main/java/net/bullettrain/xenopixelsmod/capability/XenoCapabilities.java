package net.bullettrain.xenopixelsmod.capability;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;

/** Persistent XenoPixels player state backed by a NeoForge 1.21 data attachment. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoCapabilities {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, XenoPixelsMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<XenoDataProvider>> XENO_DATA =
            ATTACHMENTS.register("xeno_data", () -> AttachmentType
                    .serializable(holder -> new XenoDataProvider())
                    .copyOnDeath()
                    .build());

    private XenoCapabilities() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static Optional<XenoPlayerData> get(Entity entity) {
        if (!(entity instanceof Player player)) return Optional.empty();
        return Optional.of(player.getData(XENO_DATA.get()).data());
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        get(event.getOriginal()).ifPresent(oldData ->
                get(event.getEntity()).ifPresent(newData -> newData.copyFrom(oldData)));
    }
}
