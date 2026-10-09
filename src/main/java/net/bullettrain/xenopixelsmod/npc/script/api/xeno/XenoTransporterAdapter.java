package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.transport.TransportDestination;
import net.bullettrain.xenopixelsmod.npc.transport.TransportNetwork;
import net.bullettrain.xenopixelsmod.npc.transport.TransportNetworks;
import xenoapi.npcs.api.constants.RoleType;
import xenoapi.npcs.api.entity.data.role.IRoleTransporter;

/**
 * Native transport networks do not store an origin location. A single usable destination can be
 * exposed; multiple destinations cannot be reduced to one without selecting an arbitrary row.
 * Reads of the shared world store require the logical server thread.
 */
public final class XenoTransporterAdapter implements IRoleTransporter {
    private final XenoNpcEntity npc;

    XenoTransporterAdapter(XenoNpcEntity npc) { this.npc = npc; }

    @Override public int getType() { return RoleType.TRANSPORTER; }

    /** Null for an absent/empty network; an ambiguous network is explicitly refused. */
    @Override public ITransportLocation getLocation() {
        XenoApiAdapters.requireServerThread(npc.level());
        TransportNetwork network = TransportNetworks.get(NpcCombatProfile.readCached(npc).transportNetwork);
        return location(network);
    }

    static ITransportLocation location(TransportNetwork network) {
        if (network == null) return null;
        TransportDestination match = null;
        for (TransportDestination destination : network.all()) {
            if (!destination.usable()) continue;
            if (match != null) {
                throw XenoApiAdapters.unsupported("IRoleTransporter.getLocation: native network has multiple destinations and no origin location");
            }
            match = destination;
        }
        return match == null ? null : new Location(match);
    }

    /** Immutable snapshot of the destination returned by the last network read. */
    public static final class Location implements ITransportLocation {
        private final TransportDestination destination;

        Location(TransportDestination destination) { this.destination = destination; }

        /** String IDs without a nonnegative integer representation have no legacy numeric ID. */
        @Override public int getId() {
            try {
                int id = Integer.parseInt(destination.id());
                return id >= 0 ? id : XenoScriptIds.NONE;
            } catch (NumberFormatException ignored) {
                return XenoScriptIds.NONE;
            }
        }
        @Override public String getDimension() { return destination.dimension(); }
        @Override public int getX() { return (int) Math.floor(destination.x()); }
        @Override public int getY() { return (int) Math.floor(destination.y()); }
        @Override public int getZ() { return (int) Math.floor(destination.z()); }
        @Override public String getName() { return destination.name(); }
        /** Native VISITED is discover (0); ALWAYS is from-start (1). There is no distinct type 2. */
        @Override public int getType() {
            return destination.unlock() == TransportDestination.Unlock.VISITED ? 0 : 1;
        }
    }
}
