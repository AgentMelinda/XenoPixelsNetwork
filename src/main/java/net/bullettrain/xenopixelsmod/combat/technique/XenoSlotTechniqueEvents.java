package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.events.DMZEvent;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Grants the Xeno slot techniques to every player.
 *
 * <p>Both hooks exist for the same reason they do on {@link XenoRushTechniqueEvents}: DMZ's own
 * data-load event covers a player whose stats arrive after login, and the login event covers one
 * whose data was already resident. {@code unlockTechnique} is idempotent, so running both is
 * harmless.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoSlotTechniqueEvents {

    private XenoSlotTechniqueEvents() {
    }

    @SubscribeEvent
    public static void onPlayerDataLoad(DMZEvent.PlayerDataLoadEvent event) {
        XenoSlotTechniques.unlock(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            XenoSlotTechniques.unlock(player);
        }
    }
}
