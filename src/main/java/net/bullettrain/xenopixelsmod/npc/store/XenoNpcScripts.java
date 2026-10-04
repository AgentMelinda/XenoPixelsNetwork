package net.bullettrain.xenopixelsmod.npc.store;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes operator-authored NPC scripts in the world store.
 *
 * <p>{@code SCRIPTS} is an <b>ungrouped</b> category, so every call passes {@code ""} as the group:
 * a script id has to be globally unique because an NPC points at exactly one of them. My NPCs keeps
 * scripts inside {@code world_data.json} alongside everything else about the NPC; ours is one file
 * per script, which is what makes a script reviewable in a diff and what stops one corrupt byte
 * from taking every script with it.
 *
 * <p><b>What is stored is text, not authority.</b> This class only moves bounded strings in and out
 * of the store. Whether that text can run at all is {@link
 * net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngine}'s question, and who may submit it is
 * {@code XenoNpcStoreWritePacket}'s (permission level 2, the same bar as every other store write).
 *
 * <p>Shape of one entry:
 * <ul>
 *   <li>{@code Name} - display label, also what the store index shows;</li>
 *   <li>{@code Language} - engine name to ask for, defaulting to {@value #DEFAULT_LANGUAGE};</li>
 *   <li>{@code Enabled} - 1/0, so an operator can retire a script without deleting it;</li>
 *   <li>{@code Script} - the source text, capped at {@value #MAX_SCRIPT_CHARS} characters.</li>
 * </ul>
 */
public final class XenoNpcScripts {

    /** {@code SCRIPTS} is ungrouped; the store still wants a group argument. */
    public static final String NO_GROUP = "";

    public static final String DEFAULT_LANGUAGE = "ecmascript";

    /**
     * Longest script we will store.
     *
     * <p>A whole NPC conversation in one script runs to a few thousand characters, so 32k is generous
     * while still keeping one entry well inside the NBT a write packet already has to carry. The cap
     * exists to make "how much can an operator send?" a number, rather than "whatever fits in a
     * packet", which is a question about a buffer limit nobody is looking at.
     */
    public static final int MAX_SCRIPT_CHARS = 32_768;

    /** Permission level to write, bind or run a script (CustomNPCs' scripter bar). */
    public static final int PERMISSION = 4;

    private static final java.util.concurrent.atomic.AtomicInteger GENERATION =
            new java.util.concurrent.atomic.AtomicInteger();

    /** Bumped on every script write; running NPC script instances rebuild when it moves. */
    public static int generation() {
        return GENERATION.get();
    }

    /** Marks stored script text as changed. */
    public static void changed() {
        GENERATION.incrementAndGet();
    }

    /** Matches the store index's own label clamp, so nothing is silently shortened later. */
    public static final int MAX_NAME_CHARS = 128;

    /** Engine names are short; the cap is here so a hand-edited file cannot carry a paragraph. */
    public static final int MAX_LANGUAGE_CHARS = 32;

    private XenoNpcScripts() {
    }

    /**
     * Whether a payload may be stored as a script, for {@code XenoNpcStoreWritePacket}.
     *
     * <p>Checked on the way in because a script nobody can run and a script that cannot be named are
     * both content an operator would lose. The text itself is not parsed here - syntax errors are
     * the engine's business, and refusing a save the engine might have accepted is not this method's
     * job.
     *
     * @return null when acceptable, else the reason shown to the operator
     */
    @Nullable
    public static String rejectPayload(@Nullable CompoundTag tag) {
        if (tag == null) {
            return "script payload is missing";
        }
        String name = tag.getString("Name");
        if (name.isBlank()) {
            return "script has no name";
        }
        if (name.length() > MAX_NAME_CHARS) {
            return "script name is longer than " + MAX_NAME_CHARS + " characters";
        }
        String language = tag.getString("Language");
        if (language.length() > MAX_LANGUAGE_CHARS) {
            return "script language is longer than " + MAX_LANGUAGE_CHARS + " characters";
        }
        if (!tag.contains("Script", net.minecraft.nbt.Tag.TAG_STRING)) {
            return "script has no source text";
        }
        if (tag.getString("Script").isBlank()) {
            return "script source text is empty";
        }
        if (tag.getString("Script").length() > MAX_SCRIPT_CHARS) {
            return "script is longer than " + MAX_SCRIPT_CHARS + " characters";
        }
        return null;
    }

    /** One stored script, or null when the id is unknown or the entry is unusable. */
    @Nullable
    public static Script load(@Nullable String id) {
        return load(XenoNpcStoreCategory.SCRIPTS, id);
    }

    @Nullable
    public static Script load(XenoNpcStoreCategory category, @Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return null;
        }
        CompoundTag tag = store.get(category, NO_GROUP, id);
        return tag == null ? null : Script.of(id, tag);
    }

    /** The source text of an enabled script, or null when there is nothing to run. */
    @Nullable
    public static String scriptText(@Nullable String id) {
        Script script = load(id);
        return script == null || !script.enabled() ? null : script.script();
    }

    @Nullable
    public static String playerScriptText(@Nullable String id) {
        Script script = load(XenoNpcStoreCategory.PLAYER_SCRIPTS, id);
        return script == null || !script.enabled() ? null : script.script();
    }

    /** Every stored script id, sorted, including disabled ones. */
    public static List<String> ids() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (XenoNpcWorldStore.Entry entry : store.list(XenoNpcStoreCategory.SCRIPTS)) {
            out.add(entry.id());
        }
        out.sort(String::compareTo);
        return List.copyOf(out);
    }

    /** How many scripts are stored. */
    public static int total() {
        XenoNpcWorldStore store = XenoNpcStores.get();
        return store == null ? 0 : store.list(XenoNpcStoreCategory.SCRIPTS).size();
    }

    /**
     * Writes one script, bypassing the network path.
     *
     * <p>For the importer and for tests. An operator's editor write goes through the packet, which
     * carries a revision so two operators cannot overwrite each other blind; this is the unchecked
     * programmatic write and says so in its name.
     *
     * @return null on success, or the store's reason for refusing
     */
    @Nullable
    public static String putUnchecked(@Nullable String id, @Nullable CompoundTag payload) {
        XenoNpcWorldStore store = XenoNpcStores.get();
        if (store == null) {
            return "the NPC store is not open";
        }
        String bad = XenoNpcStorePaths.reject(id);
        if (bad != null) {
            return bad;
        }
        String refused = store.put(XenoNpcStoreCategory.SCRIPTS, NO_GROUP, id, payload);
        if (refused == null) {
            changed();
        }
        return refused;
    }

    /** A parsed script entry. Deliberately a record, not a live object: it is a snapshot of text. */
    public record Script(String id, String name, String language, boolean enabled, String script) {

        public static Script of(String id, CompoundTag tag) {
            String name = tag.getString("Name");
            String language = tag.getString("Language").trim().toLowerCase(Locale.ROOT);
            return new Script(id,
                    name.isBlank() ? id : name,
                    language.isBlank() ? DEFAULT_LANGUAGE : language,
                    // Absent means enabled: a hand-written file that only carries Name and Script
                    // should run, rather than sitting there looking authored-but-dead.
                    !tag.contains("Enabled", net.minecraft.nbt.Tag.TAG_BYTE)
                            || tag.getByte("Enabled") != 0,
                    tag.getString("Script"));
        }

        /** The NBT the store writes; round-trips through {@link #of}. */
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Language", language);
            tag.putByte("Enabled", (byte) (enabled ? 1 : 0));
            tag.putString("Script", script);
            return tag;
        }
    }
}
