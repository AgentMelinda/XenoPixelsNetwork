package net.bullettrain.xenopixelsmod.compat.yawp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Our own ki-griefing flags, stored per YAWP region.
 *
 * <p>YAWP cannot host third-party flags: {@code IFlagArgumentType} validates against
 * {@code RegionFlag.contains(...)} and suggests from {@code RegionFlag.getFlagNames()}, both
 * keyed to its {@code RegionFlag} <b>enum</b>, and its {@code FlagRegister} is never consulted
 * by the command layer. So a {@code ki-griefing-*} flag could be registered but never set on a
 * region.
 *
 * <p>Instead we keep the flags ourselves and use YAWP only to answer "which region is this
 * position in" (see {@link YawpRegionLookup}). Operators get genuinely separate per-region ki
 * control without us patching YAWP.
 *
 * <p>Stored per world at {@code <world>/xenopixels/ki_region_flags.json} — this is
 * world-scoped protection data, so it belongs with the save rather than in {@code config/}.
 * Regions with no entry fall through to the configured YAWP flag mapping in
 * {@code XenoServerConfig}, so existing setups are unaffected.
 */
public final class KiRegionFlags {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Tri-state so "not configured" is distinct from an explicit allow. */
    public enum State {
        /** Defer to the configured YAWP flag mapping. */
        DEFAULT,
        /** Explicitly permit ki griefing here, even if mapped YAWP flags would deny. */
        ALLOWED,
        /** Explicitly deny ki griefing here. */
        DENIED;

        public static State byName(String name) {
            if (name == null) return DEFAULT;
            for (State state : values()) {
                if (state.name().equalsIgnoreCase(name)) return state;
            }
            return DEFAULT;
        }
    }

    /** Which actor or protected subject a flag applies to. */
    public enum Target {
        PLAYERS,
        MOBS,
        /**
         * Ki griefing near a DMZ master.
         *
         * <p>Checked before PLAYERS/MOBS, so a master's surroundings can be protected without
         * denying ki griefing to that player everywhere else in the region.
         */
        MASTERS;

        public static Target byName(String name) {
            if (name == null) return null;
            for (Target target : values()) {
                if (target.name().equalsIgnoreCase(name)) return target;
            }
            return null;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static Path path;

    private KiRegionFlags() {
    }

    private static String key(String dimension, String region) {
        return (dimension == null ? "?" : dimension.toLowerCase(Locale.ROOT))
                + "|" + (region == null ? "?" : region);
    }

    /** Resolve the effective state, or {@link State#DEFAULT} when unset. */
    public static State get(String dimension, String region, Target target) {
        if (region == null || target == null) return State.DEFAULT;
        Entry entry = ENTRIES.get(key(dimension, region));
        if (entry == null) return State.DEFAULT;
        return switch (target) {
            case PLAYERS -> State.byName(entry.players);
            case MOBS -> State.byName(entry.mobs);
            case MASTERS -> State.byName(entry.masters);
        };
    }

    /** Set a flag. {@link State#DEFAULT} on both targets removes the entry entirely. */
    public static void set(String dimension, String region, Target target, State state) {
        if (region == null || target == null) return;
        String mapKey = key(dimension, region);
        Entry entry = ENTRIES.computeIfAbsent(mapKey, ignored -> new Entry());
        switch (target) {
            case PLAYERS -> entry.players = state.name();
            case MOBS -> entry.mobs = state.name();
            case MASTERS -> entry.masters = state.name();
        }
        if (State.byName(entry.players) == State.DEFAULT
                && State.byName(entry.mobs) == State.DEFAULT
                && State.byName(entry.masters) == State.DEFAULT) {
            ENTRIES.remove(mapKey);
        }
        save();
    }

    /** Every configured region, for the list command. Keys are {@code dimension|region}. */
    public static Map<String, Entry> entries() {
        return new LinkedHashMap<>(ENTRIES);
    }

    public static void load(MinecraftServer server) {
        ENTRIES.clear();
        if (server == null) return;
        path = server.getWorldPath(LevelResource.ROOT).resolve("xenopixels")
                .resolve("ki_region_flags.json");
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null && data.regions != null) ENTRIES.putAll(data.regions);
        } catch (Exception e) {
            // Never block server start over player-editable JSON.
            XenoPixelsMod.LOGGER.warn("Failed to load ki region flags; starting empty", e);
        }
    }

    public static void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                Data data = new Data();
                data.regions = new LinkedHashMap<>(ENTRIES);
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save ki region flags", e);
        }
    }

    public static final class Entry {
        public String players = State.DEFAULT.name();
        public String mobs = State.DEFAULT.name();
        /** Absent in files written before masters support; Gson leaves the default. */
        public String masters = State.DEFAULT.name();
    }

    public static final class Data {
        public Map<String, Entry> regions;
    }
}
