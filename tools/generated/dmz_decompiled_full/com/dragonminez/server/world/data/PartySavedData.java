package com.dragonminez.server.world.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class PartySavedData extends SavedData {
   private static final String FILE_NAME = "dragonminez_parties";
   private final Map<UUID, PartySavedData.PartyInstance> parties = new HashMap<>();
   private final Map<UUID, UUID> playerPartyMap = new HashMap<>();

   public static PartySavedData get(MinecraftServer server) {
      DimensionDataStorage storage = server.getLevel(Level.OVERWORLD).getDataStorage();
      return (PartySavedData)storage.computeIfAbsent(new Factory(PartySavedData::new, PartySavedData::load), "dragonminez_parties");
   }

   public static PartySavedData load(CompoundTag tag, Provider registries) {
      PartySavedData data = new PartySavedData();
      ListTag partiesList = tag.getList("Parties", 10);

      for (int i = 0; i < partiesList.size(); i++) {
         CompoundTag partyTag = partiesList.getCompound(i);
         UUID partyId = partyTag.getUUID("PartyId");
         UUID leaderId = partyTag.getUUID("LeaderId");
         boolean pvpEnabled = partyTag.contains("PvpEnabled") && partyTag.getBoolean("PvpEnabled");
         ListTag membersList = partyTag.getList("Members", 10);
         List<UUID> members = new ArrayList<>();

         for (int j = 0; j < membersList.size(); j++) {
            CompoundTag mTag = membersList.getCompound(j);
            members.add(mTag.getUUID("Id"));
         }

         PartySavedData.PartyInstance instance = new PartySavedData.PartyInstance(partyId, leaderId, members, pvpEnabled);
         data.parties.put(partyId, instance);

         for (UUID memberId : members) {
            data.playerPartyMap.put(memberId, partyId);
         }
      }

      return data;
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag partiesList = new ListTag();

      for (PartySavedData.PartyInstance instance : this.parties.values()) {
         CompoundTag partyTag = new CompoundTag();
         partyTag.putUUID("PartyId", instance.getPartyId());
         partyTag.putUUID("LeaderId", instance.getLeaderId());
         partyTag.putBoolean("PvpEnabled", instance.isPvpEnabled());
         ListTag membersList = new ListTag();

         for (UUID memberId : instance.getMembers()) {
            CompoundTag mTag = new CompoundTag();
            mTag.putUUID("Id", memberId);
            membersList.add(mTag);
         }

         partyTag.put("Members", membersList);
         partiesList.add(partyTag);
      }

      tag.put("Parties", partiesList);
      return tag;
   }

   public PartySavedData.PartyInstance getPartyOf(UUID playerId) {
      UUID partyId = this.playerPartyMap.get(playerId);
      return partyId != null ? this.parties.get(partyId) : null;
   }

   public PartySavedData.PartyInstance getParty(UUID partyId) {
      return partyId != null ? this.parties.get(partyId) : null;
   }

   public PartySavedData.PartyInstance createParty(UUID leaderId) {
      UUID partyId = UUID.randomUUID();
      PartySavedData.PartyInstance party = new PartySavedData.PartyInstance(partyId, leaderId, new ArrayList<>(List.of(leaderId)), false);
      this.parties.put(partyId, party);
      this.playerPartyMap.put(leaderId, partyId);
      this.setDirty();
      return party;
   }

   public void removePlayer(UUID playerId) {
      UUID partyId = this.playerPartyMap.remove(playerId);
      if (partyId != null) {
         PartySavedData.PartyInstance party = this.parties.get(partyId);
         if (party != null) {
            party.getMembers().remove(playerId);
            if (party.getMembers().isEmpty()) {
               this.parties.remove(partyId);
            }
         }

         this.setDirty();
      }
   }

   public void addPlayerToParty(UUID partyId, UUID playerId) {
      PartySavedData.PartyInstance party = this.parties.get(partyId);
      if (party != null && !party.getMembers().contains(playerId)) {
         party.getMembers().add(playerId);
         this.playerPartyMap.put(playerId, partyId);
         this.setDirty();
      }
   }

   public static class PartyInstance {
      private final UUID partyId;
      private UUID leaderId;
      private final List<UUID> members;
      private boolean pvpEnabled;

      public PartyInstance(UUID partyId, UUID leaderId, List<UUID> members, boolean pvpEnabled) {
         this.partyId = partyId;
         this.leaderId = leaderId;
         this.members = new ArrayList<>(members);
         this.pvpEnabled = pvpEnabled;
      }

      public UUID getPartyId() {
         return this.partyId;
      }

      public UUID getLeaderId() {
         return this.leaderId;
      }

      public List<UUID> getMembers() {
         return this.members;
      }

      public boolean isPvpEnabled() {
         return this.pvpEnabled;
      }

      public void setLeaderId(UUID leaderId) {
         this.leaderId = leaderId;
      }

      public void setPvpEnabled(boolean pvpEnabled) {
         this.pvpEnabled = pvpEnabled;
      }
   }
}
