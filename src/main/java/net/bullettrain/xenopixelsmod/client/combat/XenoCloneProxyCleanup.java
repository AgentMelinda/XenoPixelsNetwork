package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Drops a copy's render proxy when that body leaves the world.
 *
 * <p>Each copy is drawn through a proxy of its own so it can carry its own aura and animation, and
 * those proxies are cached by the copy's UUID. Copies are created and destroyed constantly — every
 * Zanzoken ring is a handful of short-lived bodies — so without this the cache would grow for the
 * life of the session.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class XenoCloneProxyCleanup {

    private XenoCloneProxyCleanup() {
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof XenoCloneEntity clone) {
            NpcFullDmzRenderer.forgetCopy(clone.getUUID());
        }
    }
}
