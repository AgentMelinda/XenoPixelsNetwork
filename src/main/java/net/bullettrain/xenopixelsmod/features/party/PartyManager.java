package net.bullettrain.xenopixelsmod.features.party;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side parties: who is grouped with whom, and who has been invited.
 *
 * <p>{@code XenoPartyOverlay} shipped reading vanilla scoreboard teams, on the reasoning that a
 * real party system had not been authored. In practice nobody sets up scoreboard teams, so the
 * party HUD showed nothing to anyone — a finished widget with no data behind it. This is that
 * missing half.
 *
 * <p><b>Membership is session state, not save data.</b> A party is a "we are playing together
 * right now" grouping, and one that silently survived a restart would leave players in groups
 * they had forgotten joining, sharing a HUD with someone who logged off days ago. Everything here
 * lives in memory and is dropped when the server stops. That is a deliberate choice, not a
 * missing feature — if parties should persist it is a save-data decision worth making
 * explicitly.
 *
 * <p>All access is on the server thread (commands and player events), so plain maps are fine and
 * no synchronisation is used.
 */
public final class PartyManager {

    /** Cap on party size. The HUD shows four members, so a fifth would be invisible anyway. */
    public static final int MAX_MEMBERS = 5;

    /** An invite older than this is silently forgotten. */
    private static final long INVITE_TIMEOUT_MS = 120_000L;

    /** Party id to ordered members. Order is join order, so the leader is first. */
    private static final Map<UUID, Set<UUID>> PARTIES = new HashMap<>();
    /** Member to the party they are in. */
    private static final Map<UUID, UUID> MEMBERSHIP = new HashMap<>();
    /** Invitee to (party, when). One outstanding invite per player; a newer one replaces it. */
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    private PartyManager() {
    }

    private record Invite(UUID party, UUID from, long whenMs) {
    }

    /** The party this player belongs to, or null. */
    public static UUID partyOf(UUID player) {
        return MEMBERSHIP.get(player);
    }

    /** Members of this player's party including themselves, or an empty list if unpartied. */
    public static List<UUID> membersOf(UUID player) {
        UUID party = MEMBERSHIP.get(player);
        if (party == null) return List.of();
        Set<UUID> members = PARTIES.get(party);
        return members == null ? List.of() : new ArrayList<>(members);
    }

    /** True when both players are in the same party. */
    public static boolean sameParty(UUID a, UUID b) {
        UUID party = MEMBERSHIP.get(a);
        return party != null && party.equals(MEMBERSHIP.get(b));
    }

    /**
     * Invite a player. Creates a party around the inviter if they are not already in one.
     *
     * @return an error message for the inviter, or null on success
     */
    public static String invite(ServerPlayer from, ServerPlayer target) {
        if (from == null || target == null) return "No such player";
        if (from.getUUID().equals(target.getUUID())) return "You cannot invite yourself";

        UUID party = MEMBERSHIP.computeIfAbsent(from.getUUID(), id -> {
            UUID created = UUID.randomUUID();
            Set<UUID> members = new LinkedHashSet<>();
            members.add(id);
            PARTIES.put(created, members);
            return created;
        });

        Set<UUID> members = PARTIES.get(party);
        if (members == null) return "Your party no longer exists";
        if (members.contains(target.getUUID())) return target.getName().getString() + " is already in your party";
        if (members.size() >= MAX_MEMBERS) return "Your party is full (" + MAX_MEMBERS + ")";

        INVITES.put(target.getUUID(), new Invite(party, from.getUUID(), System.currentTimeMillis()));
        target.displayClientMessage(Component.literal(
                "§6" + from.getName().getString() + " invited you to their party. §e/xenoparty accept"), false);
        // Sync the inviter now: creating the party around them is itself a state change they
        // should see reflected before anyone accepts.
        sync(from.getServer(), party);
        return null;
    }

    /** @return an error message, or null on success */
    public static String accept(ServerPlayer player) {
        Invite invite = INVITES.remove(player.getUUID());
        if (invite == null) return "You have no pending party invite";
        if (System.currentTimeMillis() - invite.whenMs() > INVITE_TIMEOUT_MS) {
            return "That invite has expired";
        }
        Set<UUID> members = PARTIES.get(invite.party());
        if (members == null) return "That party no longer exists";
        if (members.size() >= MAX_MEMBERS) return "That party is full";

        // Leaving first keeps a player from being counted in two parties at once.
        leave(player, true);
        members.add(player.getUUID());
        MEMBERSHIP.put(player.getUUID(), invite.party());
        broadcast(player.getServer(), invite.party(),
                "§a" + player.getName().getString() + " joined the party");
        sync(player.getServer(), invite.party());
        return null;
    }

    /**
     * Leave the current party.
     *
     * @param quiet suppress the broadcast; used when leaving as a step of joining another party
     * @return an error message, or null on success
     */
    public static String leave(ServerPlayer player, boolean quiet) {
        UUID party = MEMBERSHIP.remove(player.getUUID());
        if (party == null) return quiet ? null : "You are not in a party";
        Set<UUID> members = PARTIES.get(party);
        MinecraftServer server = player.getServer();
        if (members != null) {
            members.remove(player.getUUID());
            if (!quiet) {
                broadcast(server, party, "§7" + player.getName().getString() + " left the party");
            }
            // A one-person party is just a player, so it is dissolved rather than left dangling
            // for them to keep inviting into.
            if (members.size() <= 1) {
                for (UUID leftover : new ArrayList<>(members)) MEMBERSHIP.remove(leftover);
                PARTIES.remove(party);
                syncMembers(server, members);
            } else {
                sync(server, party);
            }
        }
        // The leaver themselves needs an empty sync so their HUD clears.
        sendTo(player, List.of());
        return null;
    }

    /** Drop a player on logout so the rest of the party stops showing a ghost. */
    public static void onLogout(ServerPlayer player) {
        leave(player, false);
        INVITES.remove(player.getUUID());
    }

    /** Push the member list to everyone in a party. */
    public static void sync(MinecraftServer server, UUID party) {
        Set<UUID> members = PARTIES.get(party);
        if (members == null) return;
        syncMembers(server, members);
    }

    private static void syncMembers(MinecraftServer server, Set<UUID> members) {
        if (server == null) return;
        List<UUID> list = new ArrayList<>(members);
        for (UUID id : list) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) sendTo(member, list);
        }
    }

    private static void sendTo(ServerPlayer player, List<UUID> members) {
        ModNetwork.sendToPlayer(player, new PartySyncPacket(members));
    }

    private static void broadcast(MinecraftServer server, UUID party, String message) {
        Set<UUID> members = PARTIES.get(party);
        if (server == null || members == null) return;
        for (UUID id : members) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) member.displayClientMessage(Component.literal(message), false);
        }
    }

    /** Wipe everything. Called on server stop so a single-player world does not carry over. */
    public static void clear() {
        PARTIES.clear();
        MEMBERSHIP.clear();
        INVITES.clear();
    }
}
