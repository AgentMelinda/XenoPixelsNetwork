package net.bullettrain.xenopixelsmod.client;

import net.bullettrain.xenopixelsmod.features.party.PartyObjectiveSnapshot;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.bullettrain.xenopixelsmod.network.packet.PartyPingPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

/** Immutable client mirror of the authoritative DMZ-backed XenoParty state. */
@OnlyIn(Dist.CLIENT)
public final class ClientParty {
    private static volatile State state = State.EMPTY;
    private static volatile Ping ping;

    private ClientParty() {
    }

    public static void accept(PartySyncPacket packet) {
        state = new State(packet.partyId(), List.copyOf(packet.members()), packet.friendlyFire(),
                packet.expiresAtMs(), packet.pendingInviteFrom(), packet.objective(), packet.shareQuests());
    }

    public static State state() { return state; }
    public static List<PartySyncPacket.Member> members() { return state.members; }
    public static boolean active() { return state.partyId != null; }
    public static boolean contains(UUID id) { return member(id) != null; }

    public static PartySyncPacket.Member member(UUID id) {
        if (id == null) return null;
        for (PartySyncPacket.Member member : state.members) {
            if (id.equals(member.id())) return member;
        }
        return null;
    }

    public static void clear() { state = State.EMPTY; }

    public static void acceptPing(PartyPingPacket packet) {
        ping = new Ping(packet.senderId(), packet.targetId(), packet.targetEntityId(),
                packet.targetName(), packet.expiresAtGameTime());
    }

    /**
     * The live marker, or null once it has lapsed.
     *
     * <p>Pure: this is read from the render thread as well as the client tick, so expiry is done
     * by {@link #tickExpiry(long)} on the tick thread rather than as a side effect of reading.
     */
    public static Ping ping(long gameTime) {
        Ping value = ping;
        return value == null || gameTime > value.expiresAtGameTime ? null : value;
    }

    /** Drop a lapsed marker. Client tick only. */
    public static void tickExpiry(long gameTime) {
        Ping value = ping;
        if (value != null && gameTime > value.expiresAtGameTime) ping = null;
    }

    public static void clearAll() {
        state = State.EMPTY;
        ping = null;
    }

    public record State(UUID partyId, List<PartySyncPacket.Member> members, boolean friendlyFire,
                        long expiresAtMs, String pendingInviteFrom, PartyObjectiveSnapshot objective,
                        boolean shareQuests) {
        private static final State EMPTY = new State(null, List.of(), false, 0L, "",
                PartyObjectiveSnapshot.EMPTY, false);

        /** Searches this snapshot's own roster, not whatever the global state happens to be now. */
        public boolean isLeader(UUID id) {
            if (id == null) return false;
            for (PartySyncPacket.Member member : members) {
                if (id.equals(member.id())) return member.leader();
            }
            return false;
        }
    }

    public record Ping(UUID senderId, UUID targetId, int targetEntityId, String targetName,
                       long expiresAtGameTime) {
    }
}
