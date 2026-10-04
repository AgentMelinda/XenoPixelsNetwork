package net.bullettrain.xenopixelsmod.npc.transport;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Locale;

/**
 * Lifts a pre-network transporter's inline destinations into the world store.
 *
 * <p>Destinations used to be copied into each transporter's own NBT. They now live in a shared
 * network and the NPC holds a reference. An NPC saved before that change still carries the old
 * inline list, and it has to go somewhere: silently dropping it would delete an operator's work,
 * and leaving both readable would give one fact two homes — which is the thing the move exists to
 * stop.
 *
 * <p>So the list is lifted once, into a network named for the NPC, the profile is rewritten to the
 * reference, and the fact is logged. One-way: the inline list is never read again after that save.
 *
 * <p>Hooked to {@link EntityJoinLevelEvent} rather than to {@code NpcCombatProfile.read}, which
 * the per-tick NPC systems call for every profiled NPC every tick. A migration on that path would
 * be a store lookup per NPC per tick, forever, to do something that happens once.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class TransportMigration {

    /** Ids are filenames; this matches what the store will accept. */
    private static final int MAX_ID = 64;

    private TransportMigration() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof XenoNpcEntity npc)) {
            return;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        if (profile.legacyTransports == null || profile.legacyTransports.isEmpty()) {
            return;
        }
        migrate(npc, profile);
    }

    /**
     * Moves one NPC's inline list into a network and points the NPC at it.
     *
     * <p>If the NPC already serves a network, the inline list is dropped rather than merged: the
     * reference is the newer fact, and merging two lists nobody asked to combine would invent
     * destinations an operator never put together.
     */
    static void migrate(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (!profile.transportNetwork.isEmpty()) {
            XenoPixelsMod.LOGGER.info(
                    "Transport migration: '{}' already serves network '{}'; its old inline list "
                            + "was not merged in", npc.getName().getString(),
                    profile.transportNetwork);
            profile.legacyTransports = null;
            return;
        }

        String id = networkIdFor(npc);
        TransportNetwork network = new TransportNetwork(id, npc.getName().getString());
        for (var destination : profile.legacyTransports.all()) {
            network.add(destination);
        }

        String written = TransportNetworks.putIfAbsent(network);
        if (written == null) {
            // Either the id collided with a network somebody built by hand, or the store refused
            // it. Left alone and retried on the next load rather than overwriting or discarding:
            // the inline list is still in the NPC's NBT, so nothing has been lost.
            XenoPixelsMod.LOGGER.warn(
                    "Transport migration: could not create network '{}' for '{}'; its "
                            + "destinations are still on the NPC and will be retried",
                    id, npc.getName().getString());
            return;
        }

        profile.transportNetwork = written;
        profile.legacyTransports = null;
        profile.write(npc);
        XenoPixelsMod.LOGGER.info(
                "Transport migration: moved {} destination(s) from '{}' into network '{}'",
                network.size(), npc.getName().getString(), written);
    }

    /**
     * A store-legal id derived from the NPC's name.
     *
     * <p>Named for the NPC so an operator can find what became of their destinations. A name that
     * reduces to nothing falls back to the entity's UUID, which is ugly and traceable — better
     * than a random id nothing connects back to the NPC it came from.
     */
    static String networkIdFor(XenoNpcEntity npc) {
        String base = sanitise(npc.getName().getString());
        return base.isEmpty() ? "npc_" + npc.getUUID() : base;
    }

    /** The same alphabet the importer's {@code SlotIndex} folds names onto, for the same reason. */
    static String sanitise(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean lastWasSeparator = false;
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                out.append(c);
                lastWasSeparator = c == '_' || c == '-';
            } else if (Character.isWhitespace(c) && !lastWasSeparator && out.length() > 0) {
                out.append('_');
                lastWasSeparator = true;
            }
        }
        String trimmed = out.toString();
        while (!trimmed.isEmpty() && (trimmed.endsWith("_") || trimmed.endsWith("-"))) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        while (!trimmed.isEmpty() && (trimmed.startsWith("_") || trimmed.startsWith("-"))) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.length() > MAX_ID ? trimmed.substring(0, MAX_ID) : trimmed;
    }
}
