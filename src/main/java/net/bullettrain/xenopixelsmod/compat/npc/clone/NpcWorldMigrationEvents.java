package net.bullettrain.xenopixelsmod.compat.npc.clone;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Runs the CustomNPCs to My NPCs world-data migration once the server has a world open.
 *
 * <p>{@code ServerStartedEvent} rather than anything earlier: My NPCs' clone controller is what
 * knows where the world's data folder is, and it has to have started before that can be asked.
 *
 * <p>Always logs its outcome, including when there was nothing to do. A migration that runs against
 * somebody's server should leave an answer in the log to "did that happen, and what did it touch?".
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcWorldMigrationEvents {

    private NpcWorldMigrationEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!XenoServerConfig.migrateCustomNpcsWorldData) {
            return;
        }
        if (!ModList.get().isLoaded("mynpcs")) {
            return;
        }
        if (ModList.get().isLoaded("customnpcs")) {
            // Both installed: CustomNPCs still owns its own data, and copying it now would only
            // produce two diverging copies of the same world.
            XenoPixelsMod.LOGGER.info("CustomNPCs is still installed; leaving its world data alone");
            return;
        }

        NpcWorldMigrator.Result result = NpcWorldMigrator.run();
        if (result.failed()) {
            XenoPixelsMod.LOGGER.warn("CustomNPCs world data migration: {}", result.summary());
        } else if (result.changedAnything()) {
            XenoPixelsMod.LOGGER.info("CustomNPCs world data migrated to My NPCs: {}",
                    result.summary());
        } else {
            XenoPixelsMod.LOGGER.info("CustomNPCs world data migration: nothing to do ({})",
                    result.summary());
        }
    }
}
