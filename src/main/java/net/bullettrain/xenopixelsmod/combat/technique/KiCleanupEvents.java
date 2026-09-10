package net.bullettrain.xenopixelsmod.combat.technique;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Enforces persisted KI cleanup barriers whenever chunks restore their entities. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class KiCleanupEvents {
    private KiCleanupEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || !isKi(entity)) {
            return;
        }

        KiCleanupSavedData cleanup = KiCleanupSavedData.get(level.getServer());
        if (cleanup.shouldSuppress(entity, event.loadedFromDisk())) {
            event.setCanceled(true);
            entity.discard();
            return;
        }
        cleanup.markCurrent(entity);
    }

    public static boolean isKi(Entity entity) {
        return entity instanceof AbstractKiProjectile || entity instanceof KiExplosionVisualEntity;
    }
}
