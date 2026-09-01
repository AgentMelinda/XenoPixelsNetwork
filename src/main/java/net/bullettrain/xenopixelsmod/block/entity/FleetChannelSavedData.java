package net.bullettrain.xenopixelsmod.block.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent per-dimension ownership of fleet channels.
 *
 * <p>This is what makes the co-op/party channel authority survive a restart. A fleet computer's
 * {@code fleetChannel} is written into its NBT, and DragonMineZ parties are themselves saved data,
 * so both sides of the ownership contract outlive a restart — in-memory-only ownership would let
 * the first player to touch a channel re-claim it the instant the server comes back up, defeating
 * the point of the gate.
 *
 * <p>Keyed by dimension location because channels are dimension-scoped: channel 7 in the overworld
 * and channel 7 in a custom dimension are unrelated fleets (mirrors how {@link
 * FleetFireControlManager} keeps its live registry per {@link ServerLevel}).
 */
public final class FleetChannelSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_fleet_channels";
    /** dimension id → channel → authority. */
    private final Map<String, Map<Integer, ChannelAuthority>> byDimension = new HashMap<>();

    public static FleetChannelSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(FleetChannelSavedData::new, FleetChannelSavedData::load), FILE_NAME);
    }

    public static FleetChannelSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FleetChannelSavedData result = new FleetChannelSavedData();
        ListTag dims = tag.getList("Dimensions", Tag.TAG_COMPOUND);
        for (int d = 0; d < dims.size(); d++) {
            CompoundTag dimTag = dims.getCompound(d);
            String dimId = dimTag.getString("Id");
            if (dimId.isEmpty()) continue;
            Map<Integer, ChannelAuthority> channels =
                    result.byDimension.computeIfAbsent(dimId, ignored -> new HashMap<>());
            ListTag chList = dimTag.getList("Channels", Tag.TAG_COMPOUND);
            for (int c = 0; c < chList.size(); c++) {
                CompoundTag ch = chList.getCompound(c);
                int channel = ch.getInt("Channel");
                if (channel <= 0) continue;
                UUID owner = ch.hasUUID("Owner") ? ch.getUUID("Owner") : null;
                UUID party = ch.hasUUID("Party") ? ch.getUUID("Party") : null;
                if (owner == null && party == null) continue;
                channels.put(channel, new ChannelAuthority(owner, party));
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dims = new ListTag();
        for (Map.Entry<String, Map<Integer, ChannelAuthority>> dimEntry : byDimension.entrySet()) {
            if (dimEntry.getValue().isEmpty()) continue;
            CompoundTag dimTag = new CompoundTag();
            dimTag.putString("Id", dimEntry.getKey());
            ListTag chList = new ListTag();
            for (Map.Entry<Integer, ChannelAuthority> chEntry : dimEntry.getValue().entrySet()) {
                CompoundTag ch = new CompoundTag();
                ch.putInt("Channel", chEntry.getKey());
                ChannelAuthority auth = chEntry.getValue();
                if (auth.owner() != null) ch.putUUID("Owner", auth.owner());
                if (auth.party() != null) ch.putUUID("Party", auth.party());
                chList.add(ch);
            }
            dimTag.put("Channels", chList);
            dims.add(dimTag);
        }
        tag.put("Dimensions", dims);
        return tag;
    }

    public ChannelAuthority getAuthority(String dimensionId, int channel) {
        Map<Integer, ChannelAuthority> channels = byDimension.get(dimensionId);
        return channels == null ? null : channels.get(channel);
    }

    public void setAuthority(String dimensionId, int channel, UUID owner, UUID party) {
        if (channel <= 0) return;
        byDimension.computeIfAbsent(dimensionId, ignored -> new HashMap<>())
                .put(channel, new ChannelAuthority(owner, party));
        setDirty();
    }

    public void clearAuthority(String dimensionId, int channel) {
        Map<Integer, ChannelAuthority> channels = byDimension.get(dimensionId);
        if (channels != null && channels.remove(channel) != null) setDirty();
    }

    /**
     * Who controls a channel: the claiming player, plus (if they were in one at claim time) their
     * party. Either field may be null — a solo claim stores only the owner.
     */
    public record ChannelAuthority(UUID owner, UUID party) {
    }
}
