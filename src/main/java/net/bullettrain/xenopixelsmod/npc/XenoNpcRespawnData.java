package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent respawn anchors for native Xeno NPCs.
 *
 * <p>{@code respawnTick} is a <b>game time</b>, not a server tick count. It used to be
 * {@code MinecraftServer.getTickCount()}, which is a plain field that restarts at 0 every launch and
 * is never written to disk - so a deadline saved after two hours of uptime was compared, after a
 * restart, against a clock that had gone back to zero. Every NPC killed before a restart simply
 * stopped coming back until the server had run at least as long again. {@code Level.getGameTime()}
 * is persisted in {@code level.dat} under {@code "Time"}, so it survives the thing that broke this.
 *
 * <p>Entries written under the old scheme are recognised and treated as due immediately, which is
 * what an overdue respawn should be.
 */
public final class XenoNpcRespawnData extends SavedData {
    private static final String FILE_NAME = "xenopixels_npc_respawns";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_VERSION = "Version";

    /** 1 was the tick-count scheme; 2 stores game time. */
    private static final int VERSION = 2;

    /**
     * Entries are grown by player action and pruned only when their chunk happens to be loaded, so
     * without a cap a world with NPCs dying in rarely-visited chunks accumulates them forever.
     * Matches the shape of {@code KiCleanupSavedData.MAX_TOMBSTONES}.
     */
    public static final int MAX_ENTRIES = 4096;

    /**
     * How long an unclaimed entry is kept - 30 in-game days.
     *
     * <p>An entry whose chunk never loads would otherwise sit there for the life of the world. Long
     * enough that a player coming back to an old base still finds their guards; short enough that a
     * deleted outpost does not haunt the file.
     */
    public static final long ENTRY_LIFETIME = 24000L * 30L;

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Cached soonest due tick. Not saved - it is derived from {@link #entries} and is rebuilt on
     * load, so the save format is untouched.
     */
    private long earliestDue = Long.MAX_VALUE;

    /**
     * One pending respawn.
     *
     * @param respawnTick when it is due, as {@code Level.getGameTime()}
     * @param scheduledAt the game time it was scheduled at, for expiry
     */
    public record Entry(UUID anchor, ResourceLocation dimension, double x, double y, double z,
                        float yaw, float pitch, long respawnTick, XenoNpcRole role,
                        long scheduledAt, CompoundTag npcData) {}

    public static XenoNpcRespawnData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(XenoNpcRespawnData::new, XenoNpcRespawnData::load), FILE_NAME);
    }

    public static XenoNpcRespawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        XenoNpcRespawnData result = new XenoNpcRespawnData();
        int version = tag.contains(KEY_VERSION) ? tag.getInt(KEY_VERSION) : 1;
        ListTag list = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_ENTRIES, list.size()); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Anchor")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            XenoNpcRole role = XenoNpcRole.byId(entry.getString("Role"));
            if (dimension == null) continue;
            // A version-1 deadline was a server tick count and means nothing now. Zero makes it due
            // at once, which is right: it was already overdue, possibly by a long way.
            long respawnTick = version >= 2 ? entry.getLong("RespawnTick") : 0L;
            long scheduledAt = entry.contains("ScheduledAt") ? entry.getLong("ScheduledAt") : 0L;
            result.entries.add(new Entry(entry.getUUID("Anchor"), dimension,
                    entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"),
                    entry.getFloat("Yaw"), entry.getFloat("Pitch"), respawnTick,
                    role, scheduledAt, entry.getCompound("NpcData")));
        }
        // The gate is derived, not stored, so it has to be rebuilt from what was just read.
        result.recomputeEarliestDue();
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry value : entries) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Anchor", value.anchor());
            entry.putString("Dimension", value.dimension().toString());
            entry.putDouble("X", value.x());
            entry.putDouble("Y", value.y());
            entry.putDouble("Z", value.z());
            entry.putFloat("Yaw", value.yaw());
            entry.putFloat("Pitch", value.pitch());
            entry.putLong("RespawnTick", value.respawnTick());
            entry.putLong("ScheduledAt", value.scheduledAt());
            entry.putString("Role", value.role().id());
            entry.put("NpcData", value.npcData().copy());
            list.add(entry);
        }
        tag.put(KEY_ENTRIES, list);
        tag.putInt(KEY_VERSION, VERSION);
        return tag;
    }

    /**
     * Drops entries that are past their lifetime, and the oldest if there are still too many.
     *
     * <p>Called from the handler each pass. An entry is only ever consumed when its chunk happens to
     * be loaded, so nothing else would ever remove one for an NPC that died somewhere nobody goes.
     *
     * @return how many were dropped, for the log
     */
    public int prune(long now) {
        int before = entries.size();
        entries.removeIf(entry -> entry.scheduledAt() > 0L
                && now - entry.scheduledAt() > ENTRY_LIFETIME);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        int dropped = before - entries.size();
        if (dropped > 0) {
            recomputeEarliestDue();
            setDirty();
        }
        return dropped;
    }

    public void schedule(Entry entry) {
        entries.removeIf(existing -> existing.anchor().equals(entry.anchor()));
        entries.add(entry);
        earliestDue = Math.min(earliestDue, entry.respawnTick());
        setDirty();
    }

    public void cancel(UUID anchor) {
        if (entries.removeIf(entry -> entry.anchor().equals(anchor))) {
            recomputeEarliestDue();
            setDirty();
        }
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public void remove(Entry entry) {
        if (entries.remove(entry)) {
            recomputeEarliestDue();
            setDirty();
        }
    }

    /**
     * The soonest tick at which any entry becomes due, or {@link Long#MAX_VALUE} when none is.
     *
     * <p>Exists so the tick handler can decide in one comparison whether there is anything to do.
     * It used to walk every entry - up to {@link #MAX_ENTRIES} of them, twenty times a second,
     * for the sake of the few that were actually due.
     *
     * <p>Deliberately <em>not</em> a sorted list or a priority queue: {@link #entries} is
     * serialised in order, so reordering it would change the save format for no gain.
     */
    public long earliestDue() {
        return earliestDue;
    }

    /**
     * How long to wait before looking at a due entry whose chunk was not loaded.
     *
     * <p>One second. An entry only respawns when somebody is near enough for its chunk to be
     * loaded, so a due entry in an empty corner of the world stays due indefinitely - and without
     * this the gate would be permanently open, rescanning every tick, which is the exact cost the
     * gate exists to remove. Short enough that a player walking into the chunk sees the NPC
     * return promptly.
     */
    public static final long UNLOADED_RETRY_TICKS = 20L;

    /**
     * Holds the gate shut for a moment after a pass that could not consume the earliest entry.
     *
     * <p>Never moves the gate <em>earlier</em>: a scheduled entry that is genuinely sooner must
     * still win, or a fresh kill would wait behind an unreachable one.
     */
    public void deferUntil(long tick) {
        if (tick > earliestDue) {
            earliestDue = tick;
        }
    }

    /**
     * Recomputes the gate after a removal.
     *
     * <p>Only removals need the full pass. An addition can only ever move the gate earlier, which
     * is one comparison; a removal may have taken the earliest entry with it, and nothing cheaper
     * than a scan knows what is now soonest.
     */
    private void recomputeEarliestDue() {
        long soonest = Long.MAX_VALUE;
        for (Entry entry : entries) {
            soonest = Math.min(soonest, entry.respawnTick());
        }
        earliestDue = soonest;
    }
}
