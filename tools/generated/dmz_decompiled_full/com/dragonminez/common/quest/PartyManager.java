package com.dragonminez.common.quest;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.PartyInviteToastS2C;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import com.dragonminez.server.world.data.PartySavedData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Generated;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

@EventBusSubscriber(
   modid = "dragonminez"
)
public final class PartyManager {
   private static final long INVITE_DURATION_MS = 60000L;
   private static final String TEAM_PREFIX = "dmzp_";

   private PartyManager() {
   }

   private static String getTeamName(UUID partyId) {
      return "dmzp_" + partyId.toString().replace("-", "").substring(0, 11);
   }

   private static void addToMinecraftTeam(MinecraftServer server, UUID partyId, ServerPlayer player) {
      ServerScoreboard scoreboard = server.getScoreboard();
      String teamName = getTeamName(partyId);
      PlayerTeam team = scoreboard.getPlayerTeam(teamName);
      if (team == null) {
         team = scoreboard.addPlayerTeam(teamName);
         team.setAllowFriendlyFire(false);
         team.setSeeFriendlyInvisibles(true);
      }

      scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
   }

   private static void removeFromMinecraftTeam(MinecraftServer server, UUID partyId, ServerPlayer player) {
      ServerScoreboard scoreboard = server.getScoreboard();
      String teamName = getTeamName(partyId);
      PlayerTeam team = scoreboard.getPlayerTeam(teamName);
      if (team != null) {
         scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
         if (team.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(team);
         }
      }
   }

   private static void updateTeamFriendlyFire(MinecraftServer server, UUID partyId, boolean allowFriendlyFire) {
      PlayerTeam team = server.getScoreboard().getPlayerTeam(getTeamName(partyId));
      if (team != null) {
         team.setAllowFriendlyFire(allowFriendlyFire);
      }
   }

   public static UUID getOrCreateParty(ServerPlayer player) {
      PartySavedData data = PartySavedData.get(player.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
      if (party != null) {
         return party.getPartyId();
      } else {
         party = data.createParty(player.getUUID());
         addToMinecraftTeam(player.getServer(), party.getPartyId(), player);
         syncPartyToOnlineMembers(player.getServer(), party);
         return party.getPartyId();
      }
   }

   public static UUID getPartyId(ServerPlayer player) {
      PartySavedData data = PartySavedData.get(player.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
      return party != null ? party.getPartyId() : null;
   }

   public static boolean isInParty(ServerPlayer player) {
      return getPartyId(player) != null;
   }

   public static boolean isPartyLeader(ServerPlayer player) {
      PartySavedData data = PartySavedData.get(player.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
      return party != null && party.getLeaderId().equals(player.getUUID());
   }

   public static boolean canInvitePlayers(ServerPlayer player) {
      return !isInParty(player) || isPartyLeader(player);
   }

   public static boolean canClaimSharedRewards(ServerPlayer player) {
      return !isInParty(player) || isPartyLeader(player);
   }

   public static boolean areInSameParty(Player p1, Player p2) {
      StatsData data1 = getStatsData(p1);
      StatsData data2 = getStatsData(p2);
      if (data1 != null && data2 != null) {
         UUID party1 = data1.getPlayerQuestData().getActivePartyId();
         UUID party2 = data2.getPlayerQuestData().getActivePartyId();
         return party1 != null && party1.equals(party2);
      } else {
         return false;
      }
   }

   public static boolean isPartyPvpEnabled(Player player) {
      if (player instanceof ServerPlayer serverPlayer) {
         PartySavedData data = PartySavedData.get(serverPlayer.getServer());
         PartySavedData.PartyInstance party = data.getPartyOf(serverPlayer.getUUID());
         return party != null && party.isPvpEnabled();
      } else {
         StatsData data = getStatsData(player);
         return data != null && data.getPlayerQuestData().isPartyPvpEnabled();
      }
   }

   public static void togglePartyPvp(ServerPlayer leader) {
      PartySavedData data = PartySavedData.get(leader.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(leader.getUUID());
      if (party != null && party.getLeaderId().equals(leader.getUUID())) {
         party.setPvpEnabled(!party.isPvpEnabled());
         data.setDirty();
         updateTeamFriendlyFire(leader.getServer(), party.getPartyId(), party.isPvpEnabled());
         syncPartyToOnlineMembers(leader.getServer(), party);
         String state = party.isPvpEnabled() ? "✓" : "✕";
         ChatFormatting color = party.isPvpEnabled() ? ChatFormatting.RED : ChatFormatting.GREEN;

         for (ServerPlayer member : getAllPartyMembers(leader)) {
            member.sendSystemMessage(Component.translatable("quest.dmz.party.pvp.toggled", new Object[]{state}).withStyle(color));
         }
      }
   }

   public static ServerPlayer getPartyLeader(ServerPlayer player) {
      PartySavedData data = PartySavedData.get(player.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
      return party == null ? null : player.getServer().getPlayerList().getPlayer(party.getLeaderId());
   }

   public static List<ServerPlayer> getAllPartyMembers(ServerPlayer player) {
      PartySavedData data = PartySavedData.get(player.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
      if (party == null) {
         return Collections.singletonList(player);
      } else {
         List<ServerPlayer> members = new ArrayList<>();

         for (UUID memberId : party.getMembers()) {
            ServerPlayer member = player.getServer().getPlayerList().getPlayer(memberId);
            if (member != null && !members.contains(member)) {
               members.add(member);
            }
         }

         if (members.isEmpty()) {
            members.add(player);
         }

         return members;
      }
   }

   private static boolean validateLevelGap(ServerPlayer leader, ServerPlayer target) {
      int maxGap = ConfigManager.getServerConfig().getGameplay().getPartyMaxLevelGap();
      if (maxGap == -1) {
         return true;
      } else {
         StatsData leaderData = getStatsData(leader);
         StatsData targetData = getStatsData(target);
         if (leaderData != null && targetData != null) {
            int leaderLevel = leaderData.getLevel();
            int targetLevel = targetData.getLevel();
            return Math.abs(leaderLevel - targetLevel) <= maxGap;
         } else {
            return false;
         }
      }
   }

   public static PartyManager.InviteRequestResult requestInvite(ServerPlayer inviter, ServerPlayer invitee) {
      if (inviter.getUUID().equals(invitee.getUUID())) {
         return PartyManager.InviteRequestResult.CANNOT_INVITE_SELF;
      } else if (isInParty(invitee)) {
         return PartyManager.InviteRequestResult.ALREADY_IN_PARTY;
      } else {
         ServerPlayer resolvedLeader = isInParty(inviter) ? getPartyLeader(inviter) : inviter;
         if (resolvedLeader != null && !validateLevelGap(resolvedLeader, invitee)) {
            return PartyManager.InviteRequestResult.LEVEL_GAP;
         } else {
            int maxMembers = ConfigManager.getServerConfig().getGameplay().getPartyMaxMembers();
            if (maxMembers != -1) {
               if (isInParty(inviter) && getAllPartyMembers(inviter).size() >= maxMembers) {
                  return PartyManager.InviteRequestResult.PARTY_FULL;
               }

               if (!isInParty(inviter) && maxMembers < 2) {
                  return PartyManager.InviteRequestResult.PARTY_FULL;
               }
            }

            if (isInParty(inviter) && !isPartyLeader(inviter)) {
               ServerPlayer leader = getPartyLeader(inviter);
               if (leader != null) {
                  Component acceptBtn = Component.translatable("quest.dmz.party.invite.button")
                     .withStyle(
                        style -> style.withColor(ChatFormatting.GREEN)
                              .withBold(true)
                              .withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/dmzparty invite " + invitee.getGameProfile().getName()))
                     );
                  leader.sendSystemMessage(
                     Component.translatable(
                           "quest.dmz.party.invite.suggest", new Object[]{inviter.getGameProfile().getName(), invitee.getGameProfile().getName()}
                        )
                        .append(Component.literal(" "))
                        .append(acceptBtn)
                  );
                  return PartyManager.InviteRequestResult.SUGGESTED;
               } else {
                  return PartyManager.InviteRequestResult.NO_PERMISSION;
               }
            } else {
               sendInvite(inviter, invitee);
               return PartyManager.InviteRequestResult.INVITED;
            }
         }
      }
   }

   public static void sendInvite(ServerPlayer inviter, ServerPlayer invitee) {
      UUID partyId = getOrCreateParty(inviter);
      ServerPlayer leader = getPartyLeader(inviter);
      if (leader == null) {
         leader = inviter;
      }

      PlayerQuestData inviteeQuestData = getQuestData(invitee);
      long expiresAt = System.currentTimeMillis() + 60000L;
      inviteeQuestData.setPendingPartyInvite(
         new PlayerQuestData.PartyInviteData(
            inviter.getUUID(), partyId, leader.getUUID(), inviter.getGameProfile().getName(), expiresAt, getQuestData(leader).getDifficulty()
         )
      );
      syncSelf(invitee);
      NetworkHandler.sendToPlayer(new PartyInviteToastS2C(inviter.getGameProfile().getName()), invitee);
   }

   public static PartyManager.InviteAcceptResult acceptInvite(ServerPlayer inviter) {
      return acceptInvite(inviter, false);
   }

   public static PartyManager.InviteAcceptResult acceptInvite(ServerPlayer invitee, boolean confirmedDifficultyChange) {
      PlayerQuestData inviteeQuestData = getQuestData(invitee);
      PlayerQuestData.PartyInviteData invite = inviteeQuestData.getPendingPartyInviteData();
      if (invite == null) {
         return PartyManager.InviteAcceptResult.INVALID;
      } else {
         ServerPlayer resolvedLeader = isInParty(invitee) ? getPartyLeader(invitee) : invitee;
         if (resolvedLeader != null && !validateLevelGap(resolvedLeader, invitee)) {
            return PartyManager.InviteAcceptResult.LEVEL_GAP;
         } else if (invite.isExpired()) {
            inviteeQuestData.clearPendingPartyInvite();
            syncSelf(invitee);
            return PartyManager.InviteAcceptResult.EXPIRED;
         } else {
            ServerPlayer leader = invitee.getServer().getPlayerList().getPlayer(invite.getPartyLeaderId());
            if (leader != null && isPartyLeader(leader) && Objects.equals(getPartyId(leader), invite.getPartyId())) {
               int maxMembers = ConfigManager.getServerConfig().getGameplay().getPartyMaxMembers();
               if (maxMembers != -1 && getAllPartyMembers(leader).size() >= maxMembers) {
                  inviteeQuestData.clearPendingPartyInvite();
                  syncSelf(invitee);
                  return PartyManager.InviteAcceptResult.PARTY_FULL;
               } else {
                  Difficulty partyDifficulty = getQuestData(leader).getDifficulty();
                  Difficulty ownDifficulty = inviteeQuestData.getDifficulty();
                  if (partyDifficulty.ordinal() < ownDifficulty.ordinal()) {
                     return PartyManager.InviteAcceptResult.DIFFICULTY_TOO_LOW;
                  } else if (partyDifficulty.ordinal() > ownDifficulty.ordinal() && !confirmedDifficultyChange) {
                     return PartyManager.InviteAcceptResult.DIFFICULTY_CONFIRM_REQUIRED;
                  } else {
                     inviteeQuestData.clearPendingPartyInvite();
                     leaveParty(invitee);
                     joinLeaderParty(leader, invitee);
                     return PartyManager.InviteAcceptResult.SUCCESS;
                  }
               }
            } else {
               inviteeQuestData.clearPendingPartyInvite();
               syncSelf(invitee);
               return PartyManager.InviteAcceptResult.INVALID;
            }
         }
      }
   }

   public static void rejectInvite(ServerPlayer invitee) {
      PlayerQuestData inviteeQuestData = getQuestData(invitee);
      inviteeQuestData.clearPendingPartyInvite();
      syncSelf(invitee);
   }

   public static PartyManager.PendingInvite getPendingInvite(ServerPlayer player) {
      PlayerQuestData.PartyInviteData invite = getQuestData(player).getPendingPartyInviteData();
      return invite == null
         ? null
         : new PartyManager.PendingInvite(
            invite.getInviterUUID(),
            invite.getPartyId() == null ? "" : invite.getPartyId().toString(),
            invite.getPartyLeaderId(),
            invite.getInviterName(),
            invite.getExpiresAtMs(),
            invite.getPartyDifficulty()
         );
   }

   public static void leaveParty(ServerPlayer player) {
      PartySavedData data = PartySavedData.get(player.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
      if (party != null) {
         boolean isLeader = party.getLeaderId().equals(player.getUUID());
         UUID partyId = party.getPartyId();
         getQuestData(player).clearPartyState();
         syncSelf(player);
         removeFromMinecraftTeam(player.getServer(), partyId, player);
         data.removePlayer(player.getUUID());
         if (isLeader && !party.getMembers().isEmpty()) {
            transferLeadership(player, party);
         } else {
            syncPartyToOnlineMembers(player.getServer(), party);
         }
      }
   }

   public static void disbandParty(ServerPlayer leader) {
      if (isInParty(leader)) {
         List<ServerPlayer> members = getAllPartyMembers(leader);
         syncPartyQuestState(leader);

         for (ServerPlayer member : members) {
            if (!member.equals(leader)) {
               leaveParty(member);
            }
         }

         leaveParty(leader);
      }
   }

   public static void syncPartyQuestState(ServerPlayer sourcePlayer) {
      ServerPlayer leader = resolveQuestController(sourcePlayer);
      if (leader != null) {
         for (ServerPlayer member : getAllPartyMembers(leader)) {
            if (!member.getUUID().equals(leader.getUUID())) {
               syncQuestProgress(leader, member);
            }

            syncSelf(member);
         }
      }
   }

   public static ServerPlayer resolveQuestController(ServerPlayer player) {
      if (isInParty(player) && !isPartyLeader(player)) {
         ServerPlayer leader = getPartyLeader(player);
         return leader != null ? leader : player;
      } else {
         return player;
      }
   }

   public static void beginFusionParty(ServerPlayer leader, ServerPlayer partner) {
      snapshotFusionParty(leader);
      snapshotFusionParty(partner);
      boolean leaderInParty = isInParty(leader);
      boolean partnerInParty = isInParty(partner);
      if (leaderInParty) {
         joinPartyForFusion(partner, getPartyId(leader), false);
      } else if (partnerInParty) {
         joinPartyForFusion(leader, getPartyId(partner), false);
      } else {
         UUID partyId = getOrCreateParty(leader);
         joinPartyForFusion(partner, partyId, false);
      }
   }

   public static void endFusionParty(ServerPlayer player) {
      StatsData data = getStatsData(player);
      if (data != null) {
         Status status = data.getStatus();
         if (status.isFusionPartyManaged()) {
            UUID prevPartyId = status.getFusionPrevPartyId();
            boolean prevLeader = status.isFusionPrevPartyLeader();
            status.setFusionPartyManaged(false);
            status.setFusionPrevPartyId(null);
            status.setFusionPrevPartyLeader(false);
            UUID currentPartyId = getPartyId(player);
            if (!Objects.equals(currentPartyId, prevPartyId)) {
               if (prevPartyId == null) {
                  leaveParty(player);
               } else {
                  joinPartyForFusion(player, prevPartyId, prevLeader);
               }
            }
         }
      }
   }

   private static void snapshotFusionParty(ServerPlayer player) {
      StatsData data = getStatsData(player);
      if (data != null) {
         Status status = data.getStatus();
         UUID partyId = getPartyId(player);
         status.setFusionPrevPartyId(partyId);
         status.setFusionPrevPartyLeader(partyId != null && isPartyLeader(player));
         status.setFusionPartyManaged(true);
      }
   }

   private static void joinPartyForFusion(ServerPlayer mover, UUID targetPartyId, boolean restoreLeadership) {
      if (targetPartyId != null) {
         if (!targetPartyId.equals(getPartyId(mover))) {
            leaveParty(mover);
            MinecraftServer server = mover.getServer();
            PartySavedData data = PartySavedData.get(server);
            PartySavedData.PartyInstance party = data.getParty(targetPartyId);
            if (party != null) {
               data.addPlayerToParty(targetPartyId, mover.getUUID());
               addToMinecraftTeam(server, targetPartyId, mover);
               updateTeamFriendlyFire(server, targetPartyId, party.isPvpEnabled());
               if (restoreLeadership) {
                  party.setLeaderId(mover.getUUID());
                  data.setDirty();

                  for (UUID id : party.getMembers()) {
                     if (!id.equals(mover.getUUID())) {
                        ServerPlayer other = server.getPlayerList().getPlayer(id);
                        if (other != null) {
                           syncQuestProgress(mover, other);
                        }
                     }
                  }
               } else {
                  ServerPlayer leader = server.getPlayerList().getPlayer(party.getLeaderId());
                  if (leader != null && !leader.getUUID().equals(mover.getUUID())) {
                     syncQuestProgress(leader, mover);
                  }
               }

               syncPartyToOnlineMembers(server, party);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         PartySavedData data = PartySavedData.get(player.getServer());
         PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
         if (party != null) {
            addToMinecraftTeam(player.getServer(), party.getPartyId(), player);
            updateTeamFriendlyFire(player.getServer(), party.getPartyId(), party.isPvpEnabled());
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         StatsData statsData = getStatsData(player);
         if (statsData != null) {
            PlayerQuestData questData = statsData.getPlayerQuestData();
            if (questData.hasPendingPartyInvite()) {
               questData.clearPendingPartyInvite();
            }
         }

         PartySavedData data = PartySavedData.get(player.getServer());
         PartySavedData.PartyInstance party = data.getPartyOf(player.getUUID());
         if (party != null) {
            if (party.getLeaderId().equals(player.getUUID())) {
               transferLeadership(player, party);
            }
         }
      }
   }

   private static void transferLeadership(ServerPlayer oldLeader, PartySavedData.PartyInstance party) {
      UUID newLeaderId = null;

      for (UUID id : party.getMembers()) {
         if (!id.equals(oldLeader.getUUID())) {
            ServerPlayer onlineMember = oldLeader.getServer().getPlayerList().getPlayer(id);
            if (onlineMember != null) {
               newLeaderId = id;
               break;
            }
         }
      }

      if (newLeaderId != null) {
         party.setLeaderId(newLeaderId);
         PartySavedData.get(oldLeader.getServer()).setDirty();
         syncPartyToOnlineMembers(oldLeader.getServer(), party);
         ServerPlayer finalNewLeader = oldLeader.getServer().getPlayerList().getPlayer(newLeaderId);
         String leaderName = finalNewLeader != null ? finalNewLeader.getGameProfile().getName() : "Unknown";

         for (UUID idx : party.getMembers()) {
            ServerPlayer member = oldLeader.getServer().getPlayerList().getPlayer(idx);
            if (member != null) {
               member.sendSystemMessage(Component.translatable("quest.dmz.party.leader.transferred", new Object[]{leaderName}).withStyle(ChatFormatting.YELLOW));
            }
         }
      }
   }

   private static void joinLeaderParty(ServerPlayer leader, ServerPlayer member) {
      UUID partyId = getOrCreateParty(leader);
      PartySavedData data = PartySavedData.get(leader.getServer());
      PartySavedData.PartyInstance party = data.getPartyOf(leader.getUUID());
      PlayerQuestData memberData = getQuestData(member);
      memberData.setDifficultyChosen(true);
      syncQuestProgress(leader, member);
      data.addPlayerToParty(partyId, member.getUUID());
      addToMinecraftTeam(leader.getServer(), partyId, leader);
      addToMinecraftTeam(leader.getServer(), partyId, member);
      updateTeamFriendlyFire(leader.getServer(), partyId, party.isPvpEnabled());
      syncPartyToOnlineMembers(leader.getServer(), party);
   }

   private static void syncPartyToOnlineMembers(MinecraftServer server, PartySavedData.PartyInstance party) {
      List<UUID> memberIds = party.getMembers();

      for (UUID id : memberIds) {
         ServerPlayer member = server.getPlayerList().getPlayer(id);
         if (member != null) {
            getQuestData(member).setPartyState(party.getPartyId(), party.getLeaderId(), memberIds, party.isPvpEnabled());
            syncSelf(member);
         }
      }
   }

   private static void syncQuestProgress(ServerPlayer fromPlayer, ServerPlayer toPlayer) {
      StatsData fromData = getStatsData(fromPlayer);
      StatsData toData = getStatsData(toPlayer);
      if (fromData != null && toData != null) {
         toData.getPlayerQuestData().mergeQuestStateFrom(fromData.getPlayerQuestData());
      }
   }

   private static void syncSelf(ServerPlayer player) {
      NetworkHandler.sendToPlayer(new ProgressionSyncS2C(player), player);
   }

   private static PlayerQuestData getQuestData(Player player) {
      StatsData data = getStatsData(player);
      if (data == null) {
         throw new IllegalStateException("Missing stats capability for player " + player.getGameProfile().getName());
      } else {
         return data.getPlayerQuestData();
      }
   }

   private static StatsData getStatsData(Player player) {
      return StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
   }

   public static enum InviteAcceptResult {
      SUCCESS,
      EXPIRED,
      PARTY_FULL,
      INVALID,
      LEVEL_GAP,
      DIFFICULTY_TOO_LOW,
      DIFFICULTY_CONFIRM_REQUIRED;
   }

   public static enum InviteRequestResult {
      INVITED,
      SUGGESTED,
      PARTY_FULL,
      ALREADY_IN_PARTY,
      NO_PERMISSION,
      LEVEL_GAP,
      CANNOT_INVITE_SELF;
   }

   public static class PendingInvite {
      private final UUID inviterUUID;
      private final String teamName;
      private final UUID partyLeaderId;
      private final String inviterName;
      private final long expiresAtMs;
      private final Difficulty partyDifficulty;

      public PendingInvite(UUID inviterUUID, String teamName, UUID partyLeaderId, String inviterName, long expiresAtMs, Difficulty partyDifficulty) {
         this.inviterUUID = inviterUUID;
         this.teamName = teamName;
         this.partyLeaderId = partyLeaderId;
         this.inviterName = inviterName == null ? "" : inviterName;
         this.expiresAtMs = expiresAtMs;
         this.partyDifficulty = partyDifficulty == null ? Difficulty.NORMAL : partyDifficulty;
      }

      public boolean isExpired() {
         return System.currentTimeMillis() > this.expiresAtMs;
      }

      public UUID getPartyId() {
         try {
            return this.teamName != null && !this.teamName.isBlank() ? UUID.fromString(this.teamName) : null;
         } catch (IllegalArgumentException var2) {
            return null;
         }
      }

      @Generated
      public UUID getInviterUUID() {
         return this.inviterUUID;
      }

      @Generated
      public String getTeamName() {
         return this.teamName;
      }

      @Generated
      public UUID getPartyLeaderId() {
         return this.partyLeaderId;
      }

      @Generated
      public String getInviterName() {
         return this.inviterName;
      }

      @Generated
      public Difficulty getPartyDifficulty() {
         return this.partyDifficulty;
      }
   }
}
