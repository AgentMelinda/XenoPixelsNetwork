package net.bullettrain.xenopixelsmod.features.party;

import com.dragonminez.common.quest.PartyManager.InviteAcceptResult;
import com.dragonminez.common.quest.PartyManager.InviteRequestResult;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.server.world.data.PartySavedData;
import com.mojang.authlib.GameProfile;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.config.XenoPartyConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.PartySyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Xeno policy and presentation façade over DragonMineZ's authoritative quest-aware parties.
 * There is deliberately no second membership map here.
 */
public final class PartyManager {
    private static final String DMZ_TEAM_PREFIX = "dmzp_";

    private PartyManager() {
    }

    public static UUID partyOf(ServerPlayer player) {
        return player == null ? null : com.dragonminez.common.quest.PartyManager.getPartyId(player);
    }

    public static List<UUID> membersOf(ServerPlayer player) {
        UUID id = partyOf(player);
        if (id == null) return List.of();
        PartySavedData.PartyInstance party = PartySavedData.get(player.getServer()).getParty(id);
        return party == null ? List.of() : List.copyOf(party.getMembers());
    }

    /** Member count without copying the roster, for the capacity checks that only need a size. */
    public static int memberCount(ServerPlayer player) {
        UUID id = partyOf(player);
        if (id == null) return 0;
        PartySavedData.PartyInstance party = PartySavedData.get(player.getServer()).getParty(id);
        return party == null ? 0 : party.getMembers().size();
    }

    public static boolean sameParty(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null || a.getServer() != b.getServer()) return false;
        UUID first = partyOf(a);
        return first != null && first.equals(partyOf(b));
    }

    /**
     * True if a party with this id still exists. Used to tell a live channel owner apart from a
     * stale one (disbanded or idle-expired) — a stale authority should be releasable rather than
     * locking its channel number forever.
     */
    public static boolean partyExists(MinecraftServer server, UUID partyId) {
        if (server == null || partyId == null) return false;
        return PartySavedData.get(server).getParty(partyId) != null;
    }

    public static boolean isLeader(ServerPlayer player) {
        return player != null && com.dragonminez.common.quest.PartyManager.isPartyLeader(player);
    }

    public static String invite(ServerPlayer from, ServerPlayer target) {
        if (from == null || target == null) return "No such player";
        if (!isLeader(from) && partyOf(from) != null) return "Only the party leader can invite players";
        if (memberCount(from) >= XenoPartyConfig.maxMembers) {
            return "Your party is full (" + XenoPartyConfig.maxMembers + ")";
        }
        InviteRequestResult result = com.dragonminez.common.quest.PartyManager.requestInvite(from, target);
        UUID partyId = partyOf(from);
        if (result == InviteRequestResult.INVITED && partyId != null) {
            PartySavedData.PartyInstance party = PartySavedData.get(from.getServer()).getParty(partyId);
            if (party != null && XenoPartyConfig.friendlyFireDefault != party.isPvpEnabled()) {
                party.setPvpEnabled(XenoPartyConfig.friendlyFireDefault);
                PartySavedData.get(from.getServer()).setDirty();
            }
            touch(from.getServer(), partyId);
            syncParty(from.getServer(), partyId);
            // The invitee is not in this party, so syncParty does not reach them and the periodic
            // push only covers players who already have a party. Without this the invite banner
            // never appears until they happen to open the screen.
            sendState(target);
            return null;
        }
        return switch (result) {
            case SUGGESTED, NO_PERMISSION -> "Only the party leader can invite players";
            case PARTY_FULL -> "That party is full";
            case ALREADY_IN_PARTY -> target.getName().getString() + " is already in a party";
            case LEVEL_GAP -> "DragonMineZ party level-gap rules reject that invite";
            case CANNOT_INVITE_SELF -> "You cannot invite yourself";
            case INVITED -> null;
        };
    }

    public static String accept(ServerPlayer player, boolean confirmDifficulty) {
        try {
            var invite = com.dragonminez.common.quest.PartyManager.getPendingInvite(player);
            if (invite != null && invite.getPartyId() != null) {
                PartySavedData.PartyInstance invitedParty = PartySavedData.get(player.getServer())
                        .getParty(invite.getPartyId());
                if (invitedParty != null && invitedParty.getMembers().size() >= XenoPartyConfig.maxMembers) {
                    return "That party is full (" + XenoPartyConfig.maxMembers + ")";
                }
            }
        } catch (RuntimeException ignored) {
        }
        InviteAcceptResult result = com.dragonminez.common.quest.PartyManager.acceptInvite(player, confirmDifficulty);
        if (result == InviteAcceptResult.SUCCESS) {
            UUID partyId = partyOf(player);
            touch(player.getServer(), partyId);
            syncParty(player.getServer(), partyId);
            return null;
        }
        return switch (result) {
            case EXPIRED -> "That invite expired";
            case PARTY_FULL -> "That party is full";
            case LEVEL_GAP -> "DragonMineZ party level-gap rules reject this party";
            case DIFFICULTY_TOO_LOW -> "Your quest difficulty is too high for that party";
            case DIFFICULTY_CONFIRM_REQUIRED -> "Run /xenoparty accept confirm to match the party difficulty";
            case INVALID -> "You have no valid pending invite";
            case SUCCESS -> null;
        };
    }

    public static String decline(ServerPlayer player) {
        if (com.dragonminez.common.quest.PartyManager.getPendingInvite(player) == null) {
            return "You have no pending party invite";
        }
        com.dragonminez.common.quest.PartyManager.rejectInvite(player);
        sendState(player);
        return null;
    }

    public static String leave(ServerPlayer player) {
        UUID partyId = partyOf(player);
        if (partyId == null) return "You are not in a party";
        List<UUID> oldMembers = membersOf(player);
        com.dragonminez.common.quest.PartyManager.leaveParty(player);
        sendEmpty(player);
        // oldMembers still contains the leaver, who just got sendEmpty — resyncing them too would
        // send a second, contradictory packet.
        syncMembers(player.getServer(), oldMembers, player.getUUID());
        reconcileMetadata(player.getServer(), partyId);
        return null;
    }

    public static String kick(ServerPlayer leader, ServerPlayer target) {
        return kick(leader, target == null ? null : target.getUUID());
    }

    public static String kick(ServerPlayer leader, UUID targetId) {
        if (!isLeader(leader)) return "Only the party leader can kick players";
        if (targetId == null) return "Select a party member first";
        if (leader.getUUID().equals(targetId)) return "Use /xenoparty leave or disband";
        UUID partyId = partyOf(leader);
        PartySavedData data = PartySavedData.get(leader.getServer());
        PartySavedData.PartyInstance party = data.getParty(partyId);
        if (party == null || !party.getMembers().contains(targetId)) return "That player is not in your party";
        ServerPlayer target = leader.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            com.dragonminez.common.quest.PartyManager.leaveParty(target);
            target.displayClientMessage(Component.literal("§cYou were removed from the party"), false);
            sendEmpty(target);
        } else {
            data.removePlayer(targetId);
        }
        touch(leader.getServer(), partyId);
        syncParty(leader.getServer(), partyId);
        return null;
    }

    public static String promote(ServerPlayer leader, ServerPlayer target) {
        if (!isLeader(leader)) return "Only the party leader can promote players";
        if (target == null) return "Select a party member first";
        if (!sameParty(leader, target)) return target.getName().getString() + " is not in your party";
        UUID partyId = partyOf(leader);
        PartySavedData data = PartySavedData.get(leader.getServer());
        PartySavedData.PartyInstance party = data.getParty(partyId);
        if (party == null) return "Party no longer exists";
        party.setLeaderId(target.getUUID());
        data.setDirty();
        touch(leader.getServer(), partyId);
        broadcast(leader.getServer(), partyId, "§6" + target.getName().getString() + " is now party leader");
        syncParty(leader.getServer(), partyId);
        return null;
    }

    public static String disband(ServerPlayer leader) {
        if (!isLeader(leader)) return "Only the party leader can disband the party";
        UUID partyId = partyOf(leader);
        List<UUID> members = membersOf(leader);
        com.dragonminez.common.quest.PartyManager.disbandParty(leader);
        PartyMetadataSavedData.get(leader.getServer()).remove(partyId);
        // Same cleanup expireIdle does; without it every disband leaks a scoreboard team.
        removeDmzTeam(leader.getServer(), partyId);
        for (UUID memberId : members) {
            ServerPlayer member = leader.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) sendEmpty(member);
        }
        return null;
    }

    public static String toggleFriendlyFire(ServerPlayer leader) {
        if (!isLeader(leader)) return "Only the party leader can change friendly fire";
        UUID partyId = partyOf(leader);
        com.dragonminez.common.quest.PartyManager.togglePartyPvp(leader);
        touch(leader.getServer(), partyId);
        syncParty(leader.getServer(), partyId);
        return null;
    }

    public static String chat(ServerPlayer sender, String message) {
        UUID partyId = partyOf(sender);
        if (partyId == null) return "You are not in a party";
        String clean = message == null ? "" : message.strip();
        if (clean.isEmpty()) return "Message cannot be empty";
        if (clean.length() > 256) clean = clean.substring(0, 256);
        touch(sender.getServer(), partyId);
        broadcast(sender.getServer(), partyId, "§9[Party] §b" + sender.getName().getString() + "§7: §f" + clean);
        // No syncParty: a chat line changes nothing in the roster, and the activity timestamp it
        // bumps is not something a client renders live.
        return null;
    }

    public static boolean startObjective(ServerPlayer leader, String objectiveId) {
        UUID partyId = partyOf(leader);
        if (partyId == null || !isLeader(leader)) return false;
        boolean started = PartyObjectives.start(leader, partyId, objectiveId);
        if (started) {
            touch(leader.getServer(), partyId);
            syncParty(leader.getServer(), partyId);
        }
        return started;
    }

    public static void onLogin(ServerPlayer player) {
        UUID partyId = partyOf(player);
        if (partyId != null) {
            touch(player.getServer(), partyId);
            syncParty(player.getServer(), partyId);
        } else {
            sendState(player); // includes a pending invite, if DMZ restored one
        }
    }

    /** Leader logout is explicitly activity-bound so the thirty-minute lease starts now. */
    public static void onLogout(ServerPlayer player) {
        UUID partyId = partyOf(player);
        // DMZ may transfer leadership during the same logout event. Touching for the departing
        // member guarantees the lease starts now regardless of listener ordering.
        if (partyId == null) return;
        touch(player.getServer(), partyId);
        syncParty(player.getServer(), partyId);
    }

    /** Last roster actually pushed for a party, so an unchanged one is not pushed again. */
    private static final Map<UUID, List<PartySyncPacket.Member>> LAST_SENT = new HashMap<>();
    /** Server tick of the last push per party, driving the keep-alive resend. */
    private static final Map<UUID, Integer> LAST_PUSH_TICK = new HashMap<>();
    /** Resend an unchanged roster this often, so a dropped packet cannot strand a client. */
    private static final int HEARTBEAT_TICKS = 40;

    public static void tick(MinecraftServer server) {
        int tickCount = server.getTickCount();
        if (tickCount % 10 == 0) pushActiveParties(server, tickCount);
        if (tickCount % 20 == 0) expireIdle(server);
    }

    /**
     * Push each active party's roster at most once per interval, and only when it changed.
     *
     * <p>The previous shape sent every member's full state to every member twice a second whether
     * or not anything moved, so an eight-player party rebuilt sixty-four member snapshots per sync
     * and put eight packets on the wire for no reason. Building the roster once per party and
     * diffing it against the last push turns an idle party into zero traffic.
     */
    private static void pushActiveParties(MinecraftServer server, int tickCount) {
        Set<UUID> active = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID partyId = partyOf(player);
            if (partyId == null) continue;
            if (active == null) active = new HashSet<>();
            if (!active.add(partyId)) continue; // already pushed this party for another member
            pushIfChanged(server, partyId, tickCount);
        }
        if (active == null) {
            LAST_SENT.clear();
            LAST_PUSH_TICK.clear();
            return;
        }
        LAST_SENT.keySet().retainAll(active);
        LAST_PUSH_TICK.keySet().retainAll(active);
    }

    private static void pushIfChanged(MinecraftServer server, UUID partyId, int tickCount) {
        PartySavedData.PartyInstance party = PartySavedData.get(server).getParty(partyId);
        if (party == null) {
            LAST_SENT.remove(partyId);
            LAST_PUSH_TICK.remove(partyId);
            return;
        }
        List<PartySyncPacket.Member> members = snapshotMembers(server, party);
        Integer lastPush = LAST_PUSH_TICK.get(partyId);
        boolean heartbeatDue = lastPush == null || tickCount - lastPush >= HEARTBEAT_TICKS;
        if (!heartbeatDue && members.equals(LAST_SENT.get(partyId))) return;

        LAST_SENT.put(partyId, members);
        LAST_PUSH_TICK.put(partyId, tickCount);
        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) sendState(member, party, members);
        }
    }

    public static void touch(MinecraftServer server, UUID partyId) {
        if (server != null && partyId != null) PartyMetadataSavedData.get(server).touch(partyId);
    }

    public static void syncParty(MinecraftServer server, UUID partyId) {
        if (server == null || partyId == null) return;
        PartySavedData.PartyInstance party = PartySavedData.get(server).getParty(partyId);
        if (party == null) return;
        // An explicit sync is a real change (join, kick, promote, pvp toggle), so it bypasses the
        // diff — but it still shares one roster build across every viewer.
        List<PartySyncPacket.Member> members = snapshotMembers(server, party);
        LAST_SENT.put(partyId, members);
        LAST_PUSH_TICK.put(partyId, server.getTickCount());
        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) sendState(member, party, members);
        }
    }

    private static void syncMembers(MinecraftServer server, List<UUID> members, UUID skip) {
        for (UUID memberId : members) {
            if (memberId.equals(skip)) continue;
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) sendState(member);
        }
    }

    public static void sendState(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        UUID partyId = partyOf(viewer);
        if (partyId == null) {
            String inviter = pendingInviteName(viewer);
            ModNetwork.sendToPlayer(viewer, new PartySyncPacket(null, List.of(), false, 0L,
                    inviter, PartyObjectiveSnapshot.EMPTY));
            return;
        }
        PartySavedData.PartyInstance party = PartySavedData.get(server).getParty(partyId);
        if (party == null) {
            sendEmpty(viewer);
            return;
        }
        sendState(viewer, party, snapshotMembers(server, party));
    }

    /**
     * Send a roster that has already been built to one viewer.
     *
     * <p>The roster is party-wide, but expiry, the pending invite and the objective are all
     * viewer-specific, so only those are recomputed here.
     */
    private static void sendState(ServerPlayer viewer, PartySavedData.PartyInstance party,
                                  List<PartySyncPacket.Member> members) {
        MinecraftServer server = viewer.getServer();
        UUID partyId = party.getPartyId();
        PartyMetadataSavedData metadata = PartyMetadataSavedData.get(server);
        long last = metadata.lastActivity(partyId);
        if (last <= 0L) {
            metadata.touch(partyId);
            last = metadata.lastActivity(partyId);
        }
        long expiresAt = XenoPartyConfig.idleExpirySeconds <= 0 ? 0L
                : last + XenoPartyConfig.idleExpirySeconds * 1000L;
        PartyObjectiveSnapshot objective = PartyObjectives.snapshot(viewer, partyId);
        ModNetwork.sendToPlayer(viewer, new PartySyncPacket(partyId, members, party.isPvpEnabled(),
                expiresAt, pendingInviteName(viewer), objective));
    }

    private static List<PartySyncPacket.Member> snapshotMembers(MinecraftServer server,
                                                                PartySavedData.PartyInstance party) {
        Collection<UUID> ids = party.getMembers();
        List<PartySyncPacket.Member> members = new ArrayList<>(ids.size());
        for (UUID memberId : ids) {
            members.add(snapshotMember(server, party, memberId));
        }
        return members;
    }

    private static PartySyncPacket.Member snapshotMember(MinecraftServer server,
                                                          PartySavedData.PartyInstance party,
                                                          UUID memberId) {
        ServerPlayer player = server.getPlayerList().getPlayer(memberId);
        String name = player == null ? offlineName(server, memberId) : player.getName().getString();
        int entityId = player == null ? -1 : player.getId();
        int level = 0;
        float health = 0f, maxHealth = 0f, energy = 0f, maxEnergy = 0f, stamina = 0f, maxStamina = 0f;
        int release = 0;
        String form = "";
        if (player != null) {
            health = player.getHealth();
            maxHealth = player.getMaxHealth();
            try {
                StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
                if (stats != null && stats.isDataLoaded()) {
                    level = stats.getLevel();
                    maxHealth = Math.max(maxHealth, stats.getMaxHealth());
                    if (stats.getResources() != null) {
                        energy = stats.getResources().getCurrentEnergy();
                        maxEnergy = stats.getMaxEnergy();
                        stamina = stats.getResources().getCurrentStamina();
                        maxStamina = stats.getMaxStamina();
                        release = stats.getResources().getPowerRelease();
                    }
                    if (stats.getCharacter() != null) form = stats.getCharacter().getActiveForm();
                }
            } catch (RuntimeException e) {
                XenoPixelsMod.LOGGER.debug("Party snapshot: stats unavailable for {}", memberId, e);
            }
        }
        float sparking = player == null ? 0f : Bt3SparkingSystem.getMeter(memberId);
        boolean sparkingActive = player != null && Bt3SparkingSystem.isSparking(player);
        // Quantise to whole units. Regen jitters these floats continuously, and the change
        // detection in pushIfChanged compares rosters by value — unrounded, an idle party would
        // look different every single tick and never stop resending.
        return new PartySyncPacket.Member(memberId, name, player != null,
                memberId.equals(party.getLeaderId()), entityId, level,
                round(health), round(maxHealth), round(energy), round(maxEnergy),
                round(stamina), round(maxStamina),
                release, form, round(sparking), sparkingActive);
    }

    private static float round(float value) {
        return Math.round(value);
    }

    /** Real name for an offline member, falling back to a short UUID when it is not cached. */
    private static String offlineName(MinecraftServer server, UUID memberId) {
        try {
            GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
                Optional<GameProfile> profile = cache.get(memberId);
                if (profile.isPresent() && profile.get().getName() != null) {
                    return profile.get().getName();
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to the UUID stub.
        }
        return memberId.toString().substring(0, 8);
    }

    private static String pendingInviteName(ServerPlayer viewer) {
        try {
            var invite = com.dragonminez.common.quest.PartyManager.getPendingInvite(viewer);
            return invite == null || invite.isExpired() ? "" : invite.getInviterName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void expireIdle(MinecraftServer server) {
        int seconds = XenoPartyConfig.idleExpirySeconds;
        if (seconds <= 0) return;
        long cutoff = System.currentTimeMillis() - seconds * 1000L;
        PartyMetadataSavedData metadata = PartyMetadataSavedData.get(server);
        PartySavedData parties = PartySavedData.get(server);
        for (UUID partyId : metadata.partyIds()) {
            PartySavedData.PartyInstance party = parties.getParty(partyId);
            if (party == null) {
                metadata.remove(partyId);
                continue;
            }
            long last = metadata.lastActivity(partyId);
            if (last <= 0L || last > cutoff) continue;
            List<UUID> members = List.copyOf(party.getMembers());
            broadcast(server, partyId, "§7Party expired after " + describeIdleWindow(seconds)
                    + " without activity");
            for (UUID memberId : members) {
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member != null) {
                    // Clear DMZ quest-party state and sync it before removing persistent membership.
                    com.dragonminez.common.quest.PartyManager.leaveParty(member);
                    sendEmpty(member);
                } else {
                    parties.removePlayer(memberId);
                }
            }
            removeDmzTeam(server, partyId);
            metadata.remove(partyId);
        }
    }

    /** "30 minutes" / "90 seconds" — the expiry window is configurable, so never hardcode it. */
    private static String describeIdleWindow(int seconds) {
        if (seconds % 60 != 0) return seconds + " seconds";
        int minutes = seconds / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    private static void reconcileMetadata(MinecraftServer server, UUID partyId) {
        if (partyId == null) return;
        PartySavedData.PartyInstance party = PartySavedData.get(server).getParty(partyId);
        if (party == null || party.getMembers().size() <= 1) PartyMetadataSavedData.get(server).remove(partyId);
        else touch(server, partyId);
    }

    private static void broadcast(MinecraftServer server, UUID partyId, String message) {
        PartySavedData.PartyInstance party = PartySavedData.get(server).getParty(partyId);
        if (party == null) return;
        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) member.displayClientMessage(Component.literal(message), false);
        }
    }

    /**
     * Drop the scoreboard team DragonMineZ created for this party.
     *
     * <p>The name is rebuilt rather than asked for because DMZ's {@code getTeamName} and every one
     * of its team helpers are private. This mirrors that method exactly as of DMZ 2.1.3
     * ({@code "dmzp_" + uuid-without-dashes, first 11 chars}) — verified against
     * {@code com.dragonminez.common.quest.PartyManager}. If DMZ ever changes its scheme this
     * silently stops cleaning up, so it is worth re-checking on a DMZ bump.
     */
    private static void removeDmzTeam(MinecraftServer server, UUID partyId) {
        if (partyId == null) return;
        String teamName = DMZ_TEAM_PREFIX + partyId.toString().replace("-", "").substring(0, 11);
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamName);
        if (team == null) return;
        server.getScoreboard().removePlayerTeam(team);
    }

    private static void sendEmpty(ServerPlayer player) {
        ModNetwork.sendToPlayer(player, new PartySyncPacket(null, List.of(), false, 0L, "",
                PartyObjectiveSnapshot.EMPTY));
    }
}
