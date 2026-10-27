package net.bullettrain.xenopixelsmod.space.event;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent, chart-id keyed lifecycle state for scripted chart stars. */
public final class StellarLifecycleSavedData extends SavedData {
    private static final String FILE_NAME = "xenopixels_stellar_lifecycle";
    private static final String PAUSED_AT_KEY = "LifecyclePausedAt";
    private static final String ORIGIN_X_KEY = "OriginX";
    private static final String ORIGIN_Y_KEY = "OriginY";
    private static final String ORIGIN_Z_KEY = "OriginZ";
    public static final int MAX_ENTRIES = 1024;
    private static final int MAX_STAGE_NAME_LENGTH = 48;

    /** Frozen world-space star position at the start of a supernova, independent of later orbital motion. */
    public record Origin(double x, double y, double z) {
        public Origin {
            if (!coordinate(x) || !coordinate(y) || !coordinate(z))
                throw new IllegalArgumentException("invalid stellar event origin");
        }

        private static boolean coordinate(double value) {
            return Double.isFinite(value) && Math.abs(value) <= StellarBlastWaveMath.MAX_RADIUS_BLOCKS;
        }
    }

    /** Stage is retained as text so a newer mod version can preserve states it does not yet understand. */
    public record Entry(String stage, long startedAtTick, Origin origin) {
        public Entry(String stage, long startedAtTick) { this(stage, startedAtTick, null); }

        public Entry {
            if (stage == null || stage.isEmpty() || stage.length() > MAX_STAGE_NAME_LENGTH)
                throw new IllegalArgumentException("invalid stellar stage name");
            if (startedAtTick < 0L) throw new IllegalArgumentException("invalid stellar stage start tick");
        }

        public StellarStage knownStage() {
            try {
                return StellarStage.valueOf(stage);
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    /** Rows from future/malformed schemas and duplicate ids are re-emitted unchanged instead of discarded. */
    private final java.util.List<CompoundTag> passthroughRows = new java.util.ArrayList<>();
    /** Unknown top-level fields are also retained when an older server rewrites this SavedData. */
    private final CompoundTag passthroughFields = new CompoundTag();
    /** Preserve a future Stages type at its original key and refuse mutations until this version can read it. */
    private Tag opaqueStages;
    /** Saved game tick at which the master lifecycle switch was disabled, or null when running. */
    private Long lifecyclePausedAtTick;
    /** A malformed pause field is preserved and locks writes, just like an unknown stage payload. */
    private boolean opaquePauseState;

    public static StellarLifecycleSavedData get(MinecraftServer server) {
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("overworld unavailable for stellar lifecycle storage");
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(StellarLifecycleSavedData::new, StellarLifecycleSavedData::load), FILE_NAME);
    }

    public static StellarLifecycleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new StellarLifecycleSavedData();
        for (String key : tag.getAllKeys())
            if (!"Stages".equals(key) && !PAUSED_AT_KEY.equals(key))
                data.passthroughFields.put(key, tag.get(key).copy());
        if (tag.contains(PAUSED_AT_KEY)) {
            if (tag.contains(PAUSED_AT_KEY, Tag.TAG_LONG) && tag.getLong(PAUSED_AT_KEY) >= 0L)
                data.lifecyclePausedAtTick = tag.getLong(PAUSED_AT_KEY);
            else {
                data.passthroughFields.put(PAUSED_AT_KEY, tag.get(PAUSED_AT_KEY).copy());
                data.opaquePauseState = true;
            }
        }
        Tag rawStages = tag.get("Stages");
        if (rawStages != null && !(rawStages instanceof ListTag)) {
            data.opaqueStages = rawStages.copy();
            return data;
        }
        ListTag list = rawStages instanceof ListTag rawList ? rawList : new ListTag();
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            data.opaqueStages = list.copy();
            return data;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            String id = row.getString("Id");
            String stage = row.getString("Stage");
            boolean anyOrigin = row.contains(ORIGIN_X_KEY) || row.contains(ORIGIN_Y_KEY) || row.contains(ORIGIN_Z_KEY);
            boolean fullOrigin = row.contains(ORIGIN_X_KEY, Tag.TAG_DOUBLE)
                    && row.contains(ORIGIN_Y_KEY, Tag.TAG_DOUBLE) && row.contains(ORIGIN_Z_KEY, Tag.TAG_DOUBLE);
            Origin origin = null;
            boolean validOrigin = !anyOrigin;
            if (fullOrigin) {
                try {
                    origin = new Origin(row.getDouble(ORIGIN_X_KEY), row.getDouble(ORIGIN_Y_KEY), row.getDouble(ORIGIN_Z_KEY));
                    validOrigin = true;
                } catch (IllegalArgumentException malformed) {
                    validOrigin = false;
                }
            }
            boolean recognized = validId(id) && !stage.isEmpty() && stage.length() <= MAX_STAGE_NAME_LENGTH
                    && row.contains("Started", Tag.TAG_LONG) && row.getLong("Started") >= 0L
                    && data.entries.size() < MAX_ENTRIES
                    && !data.entries.containsKey(id) && validOrigin;
            if (recognized) data.entries.put(id, new Entry(stage, row.getLong("Started"), origin));
            else data.passthroughRows.add(row.copy());
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (String key : passthroughFields.getAllKeys())
            if (!"Stages".equals(key) && !PAUSED_AT_KEY.equals(key))
                tag.put(key, passthroughFields.get(key).copy());
        if (opaquePauseState) tag.put(PAUSED_AT_KEY, passthroughFields.get(PAUSED_AT_KEY).copy());
        else if (lifecyclePausedAtTick != null) tag.putLong(PAUSED_AT_KEY, lifecyclePausedAtTick);
        if (opaqueStages != null) {
            tag.put("Stages", opaqueStages.copy());
            return tag;
        }
        ListTag list = new ListTag();
        entries.forEach((id, entry) -> {
            CompoundTag row = new CompoundTag();
            row.putString("Id", id);
            row.putString("Stage", entry.stage());
            row.putLong("Started", entry.startedAtTick());
            if (entry.origin() != null) {
                row.putDouble(ORIGIN_X_KEY, entry.origin().x());
                row.putDouble(ORIGIN_Y_KEY, entry.origin().y());
                row.putDouble(ORIGIN_Z_KEY, entry.origin().z());
            }
            list.add(row);
        });
        passthroughRows.forEach(row -> list.add(row.copy()));
        tag.put("Stages", list);
        return tag;
    }

    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public Entry entry(String id) {
        return entries.get(id);
    }

    public boolean mutationsLocked() { return opaqueStages != null || opaquePauseState; }

    /**
     * Pauses or resumes lifecycle game-time progression. While resuming, shifts only stages that had already
     * started before the pause began. A stage deliberately set while paused begins aging at resume.
     */
    public boolean reconcilePaused(boolean paused, long gameTick) {
        if (mutationsLocked() || gameTick < 0L) return false;
        if (paused) {
            if (lifecyclePausedAtTick == null) {
                lifecyclePausedAtTick = gameTick;
                setDirty();
            }
            return true;
        }
        if (lifecyclePausedAtTick == null) return true;
        long pausedAt = lifecyclePausedAtTick;
        if (gameTick > pausedAt) {
            for (var entry : new java.util.ArrayList<>(entries.entrySet())) {
                long started = entry.getValue().startedAtTick();
                long resumedStart = StellarStageTimeline.resumeStartTick(started, pausedAt, gameTick);
                if (resumedStart != started)
                    entries.put(entry.getKey(), new Entry(entry.getValue().stage(), resumedStart, entry.getValue().origin()));
            }
        }
        lifecyclePausedAtTick = null;
        setDirty();
        return true;
    }

    public boolean fullFor(String id) { return !entries.containsKey(id) && entries.size() >= MAX_ENTRIES; }

    public boolean setStage(String id, StellarStage stage, long startedAtTick) {
        Entry prior = entries.get(id);
        Origin origin = stage == StellarStage.SUPERNOVA && prior != null
                && StellarStage.SUPERNOVA.name().equals(prior.stage()) ? prior.origin() : null;
        return setStage(id, stage, startedAtTick, origin);
    }

    public boolean setStage(String id, StellarStage stage, long startedAtTick, Origin origin) {
        if (mutationsLocked() || !validId(id) || stage == null || startedAtTick < 0L) return false;
        if (stage != StellarStage.SUPERNOVA && origin != null) return false;
        if (!entries.containsKey(id) && entries.size() >= MAX_ENTRIES) return false;
        entries.put(id, new Entry(stage.name(), startedAtTick, origin));
        setDirty();
        return true;
    }

    /** Adds a missing legacy SUPERNOVA origin without changing the stage's original start tick. */
    public boolean setSupernovaOrigin(String id, Origin origin) {
        if (mutationsLocked() || !validId(id) || origin == null) return false;
        Entry prior = entries.get(id);
        if (prior == null || !StellarStage.SUPERNOVA.name().equals(prior.stage()) || prior.origin() != null) return false;
        entries.put(id, new Entry(prior.stage(), prior.startedAtTick(), origin));
        setDirty();
        return true;
    }

    private static boolean validId(String id) {
        return id != null && net.minecraft.resources.ResourceLocation.tryParse(id) != null
                && id.length() <= 256;
    }
}
