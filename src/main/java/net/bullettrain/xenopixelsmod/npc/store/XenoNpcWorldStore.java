package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The writable per-world store for authored NPC content.
 *
 * <p>Everything an operator can write from the editor lives here, under
 * {@code <world>/XenoNpcs/}. It exists because the datapack loaders cannot: a
 * {@code SimpleJsonResourceReloadListener} reads a pack and has nothing to write back to, which is
 * why every Global screen in the editor has been showing a disabled Add button.
 *
 * <p>The in-memory map is the authority and is only ever touched on the server thread. Writes go to
 * disk immediately rather than at shutdown, because losing an operator's whole editing session to a
 * crash is exactly the failure the atomic-write discipline here exists to prevent. They are small,
 * infrequent and human-paced, so the cost is not worth deferring.
 *
 * <p>Constructed from a {@link Path} rather than a server so it can be tested without one.
 */
public final class XenoNpcWorldStore {

    /** The folder under the world directory. Named for what it holds, as My NPCs' own is. */
    public static final String ROOT_FOLDER = "XenoNpcs";

    /** Per category. Beyond this an operator has a generator, not a content set. */
    public static final int MAX_PER_CATEGORY = 512;

    /**
     * Factions are capped lower, to match {@code SyncFactionsPacket.MAX_FACTIONS}.
     *
     * <p>Otherwise the store would happily hold more than the packet can carry and the surplus
     * would vanish on the way to the client with nothing said about it.
     */
    public static final int MAX_FACTIONS = 256;

    /**
     * How an entry's revision is stamped.
     *
     * <p>Incremented on every accepted write, and sent to the client with the index so a save can
     * say which version it was editing. Without it two operators on one faction silently overwrite
     * each other and both screens report success - the same failure {@code XenoNpcSavePacket}
     * already guards against per NPC with its own revision check.
     */
    public static final String TAG_REVISION = "Rev";

    /** Passed as the expected revision to mean "do not check" - for programmatic writes. */
    public static final int UNCHECKED = -1;

    /** One stored entry: its tag, and where it came from. */
    public record Entry(XenoNpcStoreCategory category, String group, String id, CompoundTag tag) {
        /** This entry's revision, or 0 for one written before revisions existed. */
        public int revision() {
            return tag == null ? 0 : tag.getInt(TAG_REVISION);
        }
    }

    private final Path root;
    private final Map<XenoNpcStoreCategory, Map<String, Entry>> entries =
            new EnumMap<>(XenoNpcStoreCategory.class);
    private final Map<XenoNpcStoreCategory, Boolean> readOnly =
            new EnumMap<>(XenoNpcStoreCategory.class);
    private final List<String> loadErrors = new ArrayList<>();
    /** Per category, what its folder looked like when memory last matched it (see diskStamp). */
    private final Map<XenoNpcStoreCategory, String> stamps = new EnumMap<>(XenoNpcStoreCategory.class);

    public XenoNpcWorldStore(Path root) {
        this.root = root;
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            entries.put(category, new LinkedHashMap<>());
            readOnly.put(category, Boolean.FALSE);
        }
    }

    public Path root() {
        return root;
    }

    /** What failed to load, so a command and the editor can say so rather than showing a hole. */
    public List<String> loadErrors() {
        return List.copyOf(loadErrors);
    }

    /**
     * Whether a category refused to load cleanly and so will not accept writes this session.
     *
     * <p>Refusing to read a file while still allowing a write over the top of it is the silent
     * truncation this whole versioning scheme exists to prevent.
     */
    public boolean isReadOnly(XenoNpcStoreCategory category) {
        return Boolean.TRUE.equals(readOnly.get(category));
    }

    /** Loads everything from disk. Safe to call again; it replaces rather than merges. */
    public void loadAll() {
        loadErrors.clear();
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            entries.get(category).clear();
            readOnly.put(category, Boolean.FALSE);
            load(category);
            stamps.put(category, diskStamp(category));
        }
    }

    /**
     * Re-reads one category when its folder changed on disk since memory last matched it: a file
     * deleted, added or edited outside the game. Writes made through this store refresh the stamp,
     * so they never count as a change.
     *
     * @return true when the category was reloaded
     */
    public boolean reloadIfChangedOnDisk(XenoNpcStoreCategory category) {
        String now = diskStamp(category);
        if (now.equals(stamps.get(category))) {
            return false;
        }
        entries.get(category).clear();
        readOnly.put(category, Boolean.FALSE);
        load(category);
        stamps.put(category, diskStamp(category));
        return true;
    }

    /** Names, sizes and modification times of a category's files, in a stable order. */
    private String diskStamp(XenoNpcStoreCategory category) {
        Path directory = root.resolve(category.folder());
        if (!Files.isDirectory(directory)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory, category.grouped() ? 2 : 1)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(XenoNpcStorePaths.EXTENSION))
                    .forEach(path -> {
                        try {
                            parts.add(directory.relativize(path) + "|" + Files.size(path) + "|"
                                    + Files.getLastModifiedTime(path).toMillis());
                        } catch (IOException e) {
                            parts.add(directory.relativize(path) + "|?");
                        }
                    });
        } catch (IOException e) {
            return "?" + e;
        }
        parts.sort(String::compareTo);
        return String.join("\n", parts);
    }

    private void load(XenoNpcStoreCategory category) {
        Path directory = root.resolve(category.folder());
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory, category.grouped() ? 2 : 1)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(
                            XenoNpcStorePaths.EXTENSION))
                    .forEach(files::add);
        } catch (IOException e) {
            fail(category, directory + ": " + e);
            return;
        }
        // Sorted, so which entries survive a cap is the same on every machine rather than whatever
        // order the filesystem happened to hand back.
        files.sort(Comparator.comparing(Path::toString));

        int cap = cap(category);
        for (Path file : files) {
            if (entries.get(category).size() >= cap) {
                fail(category, category.folder() + " holds more than " + cap
                        + " entries; the rest were not loaded");
                readOnly.put(category, Boolean.TRUE);
                return;
            }
            loadOne(category, directory, file);
        }
    }

    private void loadOne(XenoNpcStoreCategory category, Path directory, Path file) {
        String id = XenoNpcStorePaths.idFromFileName(file.getFileName().toString());
        if (id == null) {
            // A file whose *name* would never have passed the write path cannot be trusted to
            // have been written by us, so it is not read at all.
            fail(category, file + ": the filename is not a valid id");
            return;
        }
        String group = "";
        if (category.grouped()) {
            Path parent = directory.relativize(file).getParent();
            group = parent == null ? "" : parent.toString().replace('\\', '/');
            if (!XenoNpcStorePaths.isValidGroup(group)) {
                fail(category, file + ": '" + group + "' is not a valid group");
                return;
            }
        } else if (!directory.equals(file.getParent())) {
            fail(category, file + ": " + category.folder() + " has no groups");
            return;
        }

        CompoundTag tag;
        try {
            tag = SnbtFiles.read(file);
        } catch (IOException e) {
            // One bad file costs one entry. The rest of the category still loads, which is the
            // whole reason these are separate files rather than one blob.
            fail(category, file + ": " + e.getMessage());
            return;
        }

        int schema = tag.contains(XenoNpcStoreSchema.TAG, Tag.TAG_INT)
                ? tag.getInt(XenoNpcStoreSchema.TAG) : 1;
        if (schema > XenoNpcStoreSchema.CURRENT) {
            // Written by a newer build. Not read, not deleted, not overwritten: the category goes
            // read-only for the session so nothing here can truncate fields it does not know about.
            fail(category, file + ": schema " + schema + " is newer than this build's "
                    + XenoNpcStoreSchema.CURRENT + "; " + category.folder()
                    + " is read-only this session");
            readOnly.put(category, Boolean.TRUE);
            return;
        }
        tag = XenoNpcStoreSchema.upgrade(category, tag, schema);
        entries.get(category).put(key(group, id), new Entry(category, group, id, tag));
    }

    private void fail(XenoNpcStoreCategory category, String message) {
        loadErrors.add(message);
        XenoPixelsMod.LOGGER.warn("NPC store: {}", message);
    }

    private static int cap(XenoNpcStoreCategory category) {
        return category == XenoNpcStoreCategory.FACTIONS ? MAX_FACTIONS : MAX_PER_CATEGORY;
    }

    private static String key(String group, String id) {
        return group == null || group.isEmpty() ? id : group + "/" + id;
    }

    /** One entry, or null. */
    public CompoundTag get(XenoNpcStoreCategory category, String group, String id) {
        Entry entry = entries.get(category).get(key(group, id));
        return entry == null ? null : entry.tag();
    }

    /** Every entry in a category, in the order they were loaded or added. */
    public List<Entry> list(XenoNpcStoreCategory category) {
        return List.copyOf(entries.get(category).values());
    }

    /** Whether anything is stored for this category. */
    public boolean isEmpty(XenoNpcStoreCategory category) {
        return entries.get(category).isEmpty();
    }

    /** The revision an entry currently carries, or 0 when there is no such entry. */
    public int revisionOf(XenoNpcStoreCategory category, String group, String id) {
        Entry entry = entries.get(category).get(key(group, id));
        return entry == null ? 0 : entry.revision();
    }

    /** Writes one entry without checking what it is replacing. */
    public String put(XenoNpcStoreCategory category, String group, String id, CompoundTag tag) {
        return put(category, group, id, tag, UNCHECKED);
    }

    /**
     * Writes one entry, to memory and to disk.
     *
     * <p>{@code expectedRevision} is what the writer believed the entry was at - 0 for one they
     * believe is new, or {@link #UNCHECKED} to skip the check. A mismatch is refused rather than
     * applied: two operators editing one faction would otherwise overwrite each other silently,
     * with both screens reporting success and one set of edits simply gone.
     *
     * @return null on success, or why it was refused
     */
    public String put(XenoNpcStoreCategory category, String group, String id, CompoundTag tag,
                      int expectedRevision) {
        if (category == null || tag == null) {
            return "nothing to write";
        }
        if (isReadOnly(category)) {
            return category.folder() + " is read-only this session; see the load errors";
        }
        String bad = XenoNpcStorePaths.reject(id);
        if (bad != null) {
            return bad;
        }
        if (category.grouped()) {
            bad = XenoNpcStorePaths.rejectGroup(group);
            if (bad != null) {
                return bad;
            }
        }
        Path file = XenoNpcStorePaths.resolve(root, category, group, id);
        if (file == null) {
            return "that id does not resolve to a file inside the store";
        }
        Map<String, Entry> map = entries.get(category);
        if (!map.containsKey(key(group, id)) && map.size() >= cap(category)) {
            return category.folder() + " already holds its limit of " + cap(category);
        }

        int current = revisionOf(category, group, id);
        if (expectedRevision != UNCHECKED && expectedRevision != current) {
            return current == 0
                    ? "that entry no longer exists; reopen the screen"
                    : "somebody else changed that entry; reopen the screen";
        }

        CompoundTag stored = tag.copy();
        stored.putInt(XenoNpcStoreSchema.TAG, XenoNpcStoreSchema.CURRENT);
        // Bumped on every accepted write, so the next writer can say what they were editing.
        stored.putInt(TAG_REVISION, current + 1);
        try {
            SnbtFiles.write(file, stored);
        } catch (IOException e) {
            // Memory is not updated on a failed write: the two must not disagree about what is on
            // disk, or the next read would resurrect something that was never saved.
            XenoPixelsMod.LOGGER.error("NPC store: could not write {}: {}", file, e.toString());
            return "could not write the file: " + e.getMessage();
        }
        map.put(key(group, id), new Entry(category, group, id, stored));
        stamps.put(category, diskStamp(category));
        return null;
    }

    /**
     * Removes one entry.
     *
     * @return null on success, or why it was refused
     */
    public String remove(XenoNpcStoreCategory category, String group, String id) {
        if (isReadOnly(category)) {
            return category.folder() + " is read-only this session; see the load errors";
        }
        Path file = XenoNpcStorePaths.resolve(root, category, group, id);
        if (file == null) {
            return "that id does not resolve to a file inside the store";
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            return "could not delete the file: " + e.getMessage();
        }
        entries.get(category).remove(key(group, id));
        stamps.put(category, diskStamp(category));
        return null;
    }
}
