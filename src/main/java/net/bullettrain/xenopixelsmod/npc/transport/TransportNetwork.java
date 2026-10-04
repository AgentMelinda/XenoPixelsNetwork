package net.bullettrain.xenopixelsmod.npc.transport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A named set of destinations that transporter NPCs share.
 *
 * <p>Destinations used to be copied into each transporter's own NBT. A transport network is shared
 * by its nature — two transporters in one city offer the same places — so a private copy per NPC
 * meant renaming a destination required finding and editing every NPC that listed it.
 *
 * <p>This lives in the world store under {@code transport/}, and an NPC holds only the network's
 * id. It is the same shape {@code NpcDialogSlots} already uses for dialogues, and the same reason:
 * <em>would two NPCs ever want to share this, and would an author expect editing it once to change
 * both?</em>
 *
 * <p>The per-destination rules are unchanged and still live in {@link TransportDestination}. Only
 * the ownership of the list moved.
 */
public final class TransportNetwork {

    /**
     * More than this cannot be read as a list of bubbles above an NPC's head.
     *
     * <p>Carried over unchanged from the per-NPC list it replaces: the destinations are still
     * rendered as the dialogue option stack, and a taller stack still leaves the screen.
     */
    public static final int MAX_DESTINATIONS = 12;

    private static final String TAG_NAME = "Name";
    private static final String TAG_DESTINATIONS = "Destinations";

    private final String id;
    private String name;
    private final List<TransportDestination> destinations = new ArrayList<>();

    public TransportNetwork(String id, String name) {
        this.id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        this.name = name == null || name.isBlank() ? this.id : name.trim();
    }

    public String id() {
        return id;
    }

    /** What an operator sees in the picker. The id is the filename. */
    public String name() {
        return name;
    }

    public void setName(String replacement) {
        name = replacement == null || replacement.isBlank() ? id : replacement.trim();
    }

    /** Every row, including ones still being filled in. */
    public List<TransportDestination> all() {
        return Collections.unmodifiableList(destinations);
    }

    /**
     * The destinations one player may see.
     *
     * <p>An {@code ALWAYS} destination is offered to everybody; a {@code VISITED} one only when
     * this player's unlocked set names it. The set is the server's — a client that could name its
     * own unlocked destinations would be a client that could teleport anywhere this network lists.
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

    /** One destination by id, or null when this network does not offer it. */
    public TransportDestination byId(String destinationId) {
        if (destinationId == null || destinationId.isBlank()) {
            return null;
        }
        String wanted = destinationId.trim().toLowerCase(Locale.ROOT);
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_NAME, name);
        ListTag list = new ListTag();
        for (TransportDestination destination : destinations) {
            list.add(destination.save());
        }
        tag.put(TAG_DESTINATIONS, list);
        return tag;
    }

    /**
     * Reads one network.
     *
     * <p>The id comes from the filename rather than the contents — one fact, one place, the same
     * rule the faction store follows.
     */
    public static TransportNetwork load(String id, CompoundTag tag) {
        TransportNetwork network = new TransportNetwork(id,
                tag == null ? id : tag.getString(TAG_NAME));
        if (tag == null || !tag.contains(TAG_DESTINATIONS)) {
            return network;
        }
        ListTag list = tag.getList(TAG_DESTINATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_DESTINATIONS, list.size()); i++) {
            network.destinations.add(TransportDestination.load(list.getCompound(i)));
        }
        return network;
    }
}
