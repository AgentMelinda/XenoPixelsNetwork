package net.bullettrain.xenopixelsmod.features.tournament;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Overworld SavedData for the Xeno tournament queue + KotH state.
 *
 * <p>Pattern mirrors {@link net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleSavedData}
 * / {@link net.bullettrain.xenopixelsmod.block.entity.FleetChannelSavedData}.
 */
public final class TournamentSavedData extends SavedData {
    public static final String FILE_NAME = "xenopixels_tournament";

    private static final String MODE_KEY = "Mode";
    private static final String KING_KEY = "King";
    private static final String ACTIVE_KEY = "ActiveMatchId";
    private static final String NEXT_ARENA_KEY = "NextArenaIndex";
    private static final String QUEUE_KEY = "Queue";
    private static final String UUID_KEY = "UUID";
    private static final String MATCHES_KEY = "Matches";
    private static final String MATCH_ID_KEY = "MatchId";
    private static final String ENTRANT_A_KEY = "EntrantA";
    private static final String ENTRANT_B_KEY = "EntrantB";
    private static final String ARENA_KEY = "ArenaIndex";
    private static final String WINNER_KEY = "Winner";
    private static final String REPORTED_BY_KEY = "ReportedBy";
    private static final String ARENAS_KEY = "Arenas";

    private TournamentMode mode = TournamentMode.QUEUE_KOTH;
    private UUID king;
    private String activeMatchId;
    private int nextArenaIndex;
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private final LinkedHashMap<String, MatchResult> matches = new LinkedHashMap<>();
    /** Optional fight cells (spawn + AABB). When non-empty, preferred over config positions. */
    private final ArrayList<TournamentArenaRegion> arenas = new ArrayList<>();

    public static TournamentSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(
                new Factory<>(TournamentSavedData::new, TournamentSavedData::load), FILE_NAME);
    }

    public static TournamentSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TournamentSavedData result = new TournamentSavedData();
        if (tag == null) return result;
        result.mode = TournamentMode.parse(tag.getString(MODE_KEY));
        if (tag.hasUUID(KING_KEY)) {
            result.king = tag.getUUID(KING_KEY);
        }
        String active = tag.getString(ACTIVE_KEY);
        result.activeMatchId = active == null || active.isBlank() ? null : active;
        result.nextArenaIndex = Math.max(0, tag.getInt(NEXT_ARENA_KEY));

        ListTag queueList = tag.getList(QUEUE_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < queueList.size(); i++) {
            CompoundTag entry = queueList.getCompound(i);
            if (entry.hasUUID(UUID_KEY)) {
                result.queue.add(entry.getUUID(UUID_KEY));
            }
        }

        ListTag matchList = tag.getList(MATCHES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < matchList.size(); i++) {
            CompoundTag entry = matchList.getCompound(i);
            String matchId = entry.getString(MATCH_ID_KEY);
            if (matchId == null || matchId.isBlank()) continue;
            if (!entry.hasUUID(ENTRANT_A_KEY) || !entry.hasUUID(ENTRANT_B_KEY)) continue;
            UUID winner = entry.hasUUID(WINNER_KEY) ? entry.getUUID(WINNER_KEY) : null;
            result.matches.put(matchId, new MatchResult(
                    matchId,
                    entry.getUUID(ENTRANT_A_KEY),
                    entry.getUUID(ENTRANT_B_KEY),
                    Math.max(0, entry.getInt(ARENA_KEY)),
                    winner,
                    entry.getString(REPORTED_BY_KEY)));
        }

        ListTag arenaList = tag.getList(ARENAS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < arenaList.size(); i++) {
            TournamentArenaRegion region = TournamentArenaRegion.load(arenaList.getCompound(i));
            if (region != null) {
                result.arenas.add(region);
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(MODE_KEY, mode.id());
        if (king != null) {
            tag.putUUID(KING_KEY, king);
        }
        if (activeMatchId != null && !activeMatchId.isBlank()) {
            tag.putString(ACTIVE_KEY, activeMatchId);
        }
        tag.putInt(NEXT_ARENA_KEY, nextArenaIndex);

        ListTag queueList = new ListTag();
        for (UUID id : queue) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(UUID_KEY, id);
            queueList.add(entry);
        }
        tag.put(QUEUE_KEY, queueList);

        ListTag matchList = new ListTag();
        for (MatchResult match : matches.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(MATCH_ID_KEY, match.matchId());
            entry.putUUID(ENTRANT_A_KEY, match.entrantA());
            entry.putUUID(ENTRANT_B_KEY, match.entrantB());
            entry.putInt(ARENA_KEY, match.arenaIndex());
            if (match.winner() != null) {
                entry.putUUID(WINNER_KEY, match.winner());
            }
            if (match.reportedBy() != null && !match.reportedBy().isBlank()) {
                entry.putString(REPORTED_BY_KEY, match.reportedBy());
            }
            matchList.add(entry);
        }
        tag.put(MATCHES_KEY, matchList);

        ListTag arenaList = new ListTag();
        for (TournamentArenaRegion region : arenas) {
            arenaList.add(region.save());
        }
        tag.put(ARENAS_KEY, arenaList);
        return tag;
    }

    public TournamentMode mode() {
        return mode;
    }

    public void setMode(TournamentMode next) {
        TournamentMode value = next == null ? TournamentMode.QUEUE_KOTH : next;
        if (mode != value) {
            mode = value;
            setDirty();
        }
    }

    public UUID king() {
        return king;
    }

    public void setKing(UUID next) {
        if (king == null ? next == null : king.equals(next)) return;
        king = next;
        setDirty();
    }

    public String activeMatchId() {
        return activeMatchId;
    }

    public boolean hasActiveMatch() {
        return activeMatchId != null && !activeMatchId.isBlank()
                && matches.containsKey(activeMatchId)
                && !matches.get(activeMatchId).isComplete();
    }

    public int queuedCount() {
        return queue.size();
    }

    public boolean isQueued(UUID id) {
        return id != null && queue.contains(id);
    }

    public List<UUID> queuedSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    /** @return true if added */
    public boolean enqueue(UUID id) {
        if (id == null) return false;
        if (queue.contains(id)) return false;
        if (!queue.add(id)) return false;
        setDirty();
        return true;
    }

    /** @return true if removed */
    public boolean leaveQueue(UUID id) {
        if (id == null) return false;
        if (!queue.remove(id)) return false;
        setDirty();
        return true;
    }

    public UUID pollQueue() {
        if (queue.isEmpty()) return null;
        UUID next = queue.iterator().next();
        queue.remove(next);
        setDirty();
        return next;
    }

    public MatchResult match(String matchId) {
        if (matchId == null || matchId.isBlank()) return null;
        return matches.get(matchId);
    }

    public Map<String, MatchResult> matchesView() {
        return Collections.unmodifiableMap(matches);
    }

    public int nextArenaIndex() {
        return nextArenaIndex;
    }

    public void setNextArenaIndex(int index) {
        int value = Math.max(0, index);
        if (nextArenaIndex != value) {
            nextArenaIndex = value;
            setDirty();
        }
    }

    /**
     * Records a new incomplete match as the active challenge.
     * Caller must ensure no incomplete active match already exists.
     */
    public void putActiveMatch(MatchResult match) {
        if (match == null) return;
        matches.put(match.matchId(), match);
        activeMatchId = match.matchId();
        setDirty();
    }

    public void replaceMatch(MatchResult match) {
        if (match == null) return;
        matches.put(match.matchId(), match);
        if (match.isComplete() && match.matchId().equals(activeMatchId)) {
            activeMatchId = null;
        }
        setDirty();
    }

    public void clearActiveMatch() {
        if (activeMatchId != null) {
            activeMatchId = null;
            setDirty();
        }
    }

    public List<TournamentArenaRegion> arenasSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(arenas));
    }

    public int arenaCount() {
        return arenas.size();
    }

    @Nullable
    public TournamentArenaRegion arena(int index) {
        if (index < 0 || index >= arenas.size()) return null;
        return arenas.get(index);
    }

    /** Replaces arena at index, or appends when index == size / negative append sentinel. */
    public void setArena(int index, TournamentArenaRegion region) {
        if (region == null) return;
        if (index < 0 || index >= arenas.size()) {
            arenas.add(region);
        } else {
            arenas.set(index, region);
        }
        setDirty();
    }

    public boolean clearArena(int index) {
        if (index < 0 || index >= arenas.size()) return false;
        arenas.remove(index);
        setDirty();
        return true;
    }

    public void clearAllArenas() {
        if (arenas.isEmpty()) return;
        arenas.clear();
        setDirty();
    }
}
