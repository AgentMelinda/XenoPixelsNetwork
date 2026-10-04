package net.bullettrain.xenopixelsmod.npc.transport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Where one transporter NPC can send a player.
 *
 * <p>Order is the order an author typed. The list becomes buttons above the NPC's head, and a menu
 * that reshuffled itself between openings could not be learned.
 */
public final class NpcTransportList {

    /**
     * More than this cannot be read as a list of bubbles above an NPC's head.
     *
     * <p>Not an arbitrary number: the destinations are rendered as the dialogue option stack, and
     * a taller stack leaves the world and the NPC entirely.
     */
    public static final int MAX_DESTINATIONS = 12;

    private static final String TAG_DESTINATIONS = "Transports";

    private final List<TransportDestination> destinations = new ArrayList<>();

    /** Every row, including ones still being filled in. */
    public List<TransportDestination> all() {
        return Collections.unmodifiableList(destinations);
    }

    /**
     * The destinations this player may actually see.
     *
     * <p>An {@code ALWAYS} destination is offered to everybody. A {@code VISITED} one is offered
     * only when this player's unlocked set names it — and the set is the server's, never the
     * client's, because a client that could name its own unlocked destinations would be a client
     * that could teleport anywhere.
     */
    public List<TransportDestination> visibleTo(Collection<String> unlocked) {
        List<TransportDestination> out = new ArrayList<>();
        for (TransportDestination destination : destinations) {
            if (!destination.usable()) {
                continue;
            }
            if (destination.unlock() == TransportDestination.Unlock.ALWAYS
                    || (unlocked != null && unlocked.contains(destination.id()))) {
                out.add(destination);
            }
        }
        return List.copyOf(out);
    }

    /** One destination by id, or null when this NPC does not offer it. */
    public TransportDestination byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String wanted = id.trim().toLowerCase(java.util.Locale.ROOT);
        for (TransportDestination destination : destinations) {
            if (destination.id().equals(wanted)) {
                return destination;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return destinations.stream().noneMatch(TransportDestination::usable);
    }

    public int size() {
        return destinations.size();
    }

    public TransportDestination get(int index) {
        return index >= 0 && index < destinations.size()
                ? destinations.get(index) : TransportDestination.empty();
    }

    public boolean add(TransportDestination destination) {
        if (destination == null || destinations.size() >= MAX_DESTINATIONS) {
            return false;
        }
        destinations.add(destination);
        return true;
    }

    public void set(int index, TransportDestination destination) {
        if (index < 0 || index >= MAX_DESTINATIONS || destination == null) {
            return;
        }
        while (destinations.size() <= index) {
            destinations.add(TransportDestination.empty());
        }
        destinations.set(index, destination);
    }

    public void remove(int index) {
        if (index >= 0 && index < destinations.size()) {
            destinations.remove(index);
        }
    }

    /** Written only when there is something to write; most NPCs are not transporters. */
    public void saveTo(CompoundTag tag) {
        if (destinations.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (TransportDestination destination : destinations) {
            list.add(destination.save());
        }
        tag.put(TAG_DESTINATIONS, list);
    }

    public void loadFrom(CompoundTag tag) {
        destinations.clear();
        if (tag == null || !tag.contains(TAG_DESTINATIONS)) {
            return;
        }
        ListTag list = tag.getList(TAG_DESTINATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_DESTINATIONS, list.size()); i++) {
            destinations.add(TransportDestination.load(list.getCompound(i)));
        }
    }
}
