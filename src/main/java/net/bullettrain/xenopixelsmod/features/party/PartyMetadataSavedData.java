package net.bullettrain.xenopixelsmod.features.party;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Xeno-only metadata keyed by DragonMineZ's authoritative party UUID. */
public final class PartyMetadataSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_party_metadata";
    private final Map<UUID, Meta> values = new HashMap<>();

    public static PartyMetadataSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(PartyMetadataSavedData::new, PartyMetadataSavedData::load), FILE_NAME);
    }

    public static PartyMetadataSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyMetadataSavedData result = new PartyMetadataSavedData();
        ListTag list = tag.getList("Parties", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("PartyId")) continue;
            Meta meta = new Meta(entry.getLong("LastActivityMs"));
            if (entry.contains("ShareQuests")) {
                meta.shareQuestsSet = true;
                meta.shareQuests = entry.getBoolean("ShareQuests");
            }
            result.values.put(entry.getUUID("PartyId"), meta);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Meta> value : values.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("PartyId", value.getKey());
            entry.putLong("LastActivityMs", value.getValue().lastActivityMs);
            if (value.getValue().shareQuestsSet) {
                entry.putBoolean("ShareQuests", value.getValue().shareQuests);
            }
            list.add(entry);
        }
        tag.put("Parties", list);
        return tag;
    }

    public long lastActivity(UUID partyId) {
        Meta meta = values.get(partyId);
        return meta == null ? 0L : meta.lastActivityMs;
    }

    public void touch(UUID partyId) {
        if (partyId == null) return;
        values.computeIfAbsent(partyId, ignored -> new Meta(0L)).lastActivityMs = System.currentTimeMillis();
        setDirty();
    }

    public boolean shareQuests(UUID partyId) {
        Meta meta = partyId == null ? null : values.get(partyId);
        if (meta == null || !meta.shareQuestsSet) {
            return net.bullettrain.xenopixelsmod.config.XenoPartyConfig.questShareDefault;
        }
        return meta.shareQuests;
    }

    public boolean toggleShareQuests(UUID partyId) {
        if (partyId == null) return net.bullettrain.xenopixelsmod.config.XenoPartyConfig.questShareDefault;
        Meta meta = values.computeIfAbsent(partyId, ignored -> new Meta(System.currentTimeMillis()));
        boolean next = !shareQuests(partyId);
        meta.shareQuests = next;
        meta.shareQuestsSet = true;
        setDirty();
        return next;
    }

    public void remove(UUID partyId) {
        if (partyId != null && values.remove(partyId) != null) setDirty();
    }

    public Set<UUID> partyIds() {
        return Set.copyOf(values.keySet());
    }

    private static final class Meta {
        private long lastActivityMs;
        private boolean shareQuestsSet;
        private boolean shareQuests;

        private Meta(long lastActivityMs) {
            this.lastActivityMs = lastActivityMs;
        }
    }
}
