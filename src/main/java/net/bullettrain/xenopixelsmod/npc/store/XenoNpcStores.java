package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Opens the world store when a server starts, and lets go of it when one stops.
 *
 * <p>{@link ServerAboutToStartEvent} rather than a later one, so the store is populated before
 * anything can ask it a question - the faction sync fires on {@code OnDatapackSyncEvent}, which a
 * player joining can trigger early. Asking before it is open answers "nothing", which would look
 * exactly like an empty store.
 *
 * <p>Held statically because there is one integrated or dedicated server at a time, which is the
 * same assumption {@code XenoFactions} and every other loader here already makes. Cleared on stop
 * so a single-player client that quits to the menu and opens a different world does not carry the
 * first world's content into the second.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoNpcStores {

    private static volatile XenoNpcWorldStore current;

    private XenoNpcStores() {
    }

    /** The open store, or null when no server is running. */
    public static XenoNpcWorldStore get() {
        return current;
    }

    /** Whether a store is open and holds anything for this category. */
    public static boolean has(XenoNpcStoreCategory category) {
        XenoNpcWorldStore store = current;
        return store != null && !store.isEmpty(category);
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        open(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        current = null;
    }

    static void open(MinecraftServer server) {
        if (server == null) {
            return;
        }
        XenoNpcWorldStore store = new XenoNpcWorldStore(
                server.getWorldPath(new LevelResource(XenoNpcWorldStore.ROOT_FOLDER)));
        store.loadAll();
        current = store;

        int total = 0;
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            total += store.list(category).size();
        }
        if (store.loadErrors().isEmpty()) {
            XenoPixelsMod.LOGGER.info("NPC store: {} entries from {}", total, store.root());
        } else {
            XenoPixelsMod.LOGGER.warn("NPC store: {} entries from {}, {} failed to load",
                    total, store.root(), store.loadErrors().size());
        }
    }
}
