package net.bullettrain.xenopixelsmod.npc.importer;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lifts per-NPC MyNPCs / CustomNPCs scripts into the world store's {@code SCRIPTS} category.
 *
 * <p>The two mods keep scripts in opposite places. CNPC stores a list of script bodies inside the
 * NPC's own tag ({@code Scripts: [{Script, Console, ScriptList}]}, with {@code ScriptLanguage} and
 * {@code ScriptEnabled} shared at NPC level), so a script exists only as long as the NPC does. Ours
 * is one stored entry per script and an NPC that holds an id pointing at it — which is what lets
 * twelve guards share one patrol script and lets an operator review a script in a diff. So this
 * class does a shape change, not a copy: the text becomes an entry, and the NPC keeps a reference.
 *
 * <p><b>Tabs.</b> A source NPC may carry several script entries; each becomes its own tab in the
 * native NPC's script container, in source order, exactly as the CustomNPCs script screen shows them.
 *
 * <p><b>Text, not authority.</b> Nothing here decides whether a script can run. That is
 * {@code NpcScriptEngines.forLanguage}'s question at run time; a language this build has no engine
 * for is imported as text and reported by the runner. The source language is preserved (lower-cased)
 * rather than rewritten to the default, because pretending a ForgeScript is ECMAScript would make
 * the failure harder to diagnose, not easier.
 */
public final class NpcScriptImport {

    /** One converted script, before anything has been written. */
    public record Draft(String id, CompoundTag payload, boolean enabled) {

        public String name() {
            return payload.getString("Name");
        }
    }

    /** Language names the native runner answers to; anything else arrives as inert text. */
    private static final List<String> RUNNABLE_LANGUAGES = List.of(
            "ecmascript", "javascript", "js", "nashorn", "graal.js", "graaljs", "es5", "es6");

    private NpcScriptImport() {
    }

    /**
     * The store entries one source NPC yields, in source order.
     *
     * <p>Pure: no store, no filesystem, no Minecraft server, which is what makes the conversion
     * testable. Anything refused is recorded in {@code report} as it is found, so nothing vanishes
     * between here and {@link #importScripts}.
     */
    public static List<Draft> drafts(@Nullable CompoundTag source, String sourceMod, String npcKey,
                                     NpcImportReport report) {
        List<Draft> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        ListTag scripts = source.getList(PlacedNpcProfileMigrator.KEY_SCRIPTS, Tag.TAG_COMPOUND);
        if (scripts.isEmpty()) {
            return out;
        }
        String language = language(source, npcKey, report);
        // CNPC's enabled flag is one byte for the whole NPC, and absent means enabled.
        boolean enabled = !source.contains(PlacedNpcProfileMigrator.KEY_SCRIPT_ENABLED)
                || source.getByte(PlacedNpcProfileMigrator.KEY_SCRIPT_ENABLED) != 0;
        String label = scriptLabel(source, npcKey);
        SlotIndex ids = new SlotIndex();

        for (int i = 0; i < scripts.size(); i++) {
            String text = scripts.getCompound(i).getString("Script");
            if (text.isBlank()) {
                report.noteGrouped("script entry is empty, so there is nothing to import", npcKey);
                continue;
            }
            if (text.length() > XenoNpcScripts.MAX_SCRIPT_CHARS) {
                report.failed("script", npcKey + " #" + (i + 1), "script is " + text.length()
                        + " characters, over the " + XenoNpcScripts.MAX_SCRIPT_CHARS
                        + " the store holds; source text is preserved under NeoForgeData/XenoPixelsSource");
                continue;
            }
            String id = ids.assign(i, sourceMod + "_" + npcKey + "_s" + (i + 1));
            String name = scripts.size() == 1 ? label : label + " " + (i + 1);
            CompoundTag payload = new CompoundTag();
            payload.putString("Name", bounded(name, XenoNpcScripts.MAX_NAME_CHARS));
            payload.putString("Language", language);
            payload.putByte("Enabled", (byte) (enabled ? 1 : 0));
            payload.putString("Script", text);
            out.add(new Draft(id, payload, enabled));
        }
        return out;
    }

    /**
     * Writes every draft into {@code store} and returns the id to bind to this NPC.
     *
     * @param store the world store, or null when none is open (the scripts are then reported, not written)
     * @return the bound script id, or "" when there is nothing to bind
     */
    public static String importScripts(@Nullable CompoundTag source, String sourceMod, String npcKey,
                                       @Nullable XenoNpcWorldStore store, NpcImportReport report) {
        return importContainer(source, sourceMod, npcKey, store, report).firstScriptId();
    }

    /**
     * Writes every draft into {@code store} and returns the NPC's script tabs: one tab per source
     * script, in source order, with the source NPC's language and enabled switch. CustomNPCs' own
     * {@code ScriptList} names files in its scripts folder, which this import cannot see; those
     * names are reported so the operator can load the matching library scripts by hand.
     */
    public static net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer importContainer(@Nullable CompoundTag source, String sourceMod, String npcKey,
                                     @Nullable XenoNpcWorldStore store, NpcImportReport report) {
        net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer container = new net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer();
        List<Draft> drafts = drafts(source, sourceMod, npcKey, report);
        if (drafts.isEmpty()) {
            return container;
        }
        if (store == null) {
            report.failed("script", npcKey, "the NPC world store is not open; scripts were not imported");
            return container;
        }
        container.setLanguage(drafts.get(0).payload().getString("Language"));
        container.setEnabled(drafts.get(0).enabled());
        reportExternalFiles(source, npcKey, report);
        List<net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.Tab> tabs = new ArrayList<>();
        for (Draft draft : drafts) {
            if (store.get(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP, draft.id()) != null) {
                report.failed("script", draft.id(), "Xeno entry already exists; left unchanged");
                continue;
            }
            String refusal = store.put(XenoNpcStoreCategory.SCRIPTS, XenoNpcScripts.NO_GROUP,
                    draft.id(), draft.payload());
            if (refusal != null) {
                report.failed("script", draft.id(), refusal);
                continue;
            }
            report.imported("script", draft.id());
            tabs.add(new net.bullettrain.xenopixelsmod.npc.script.NpcScriptContainer.Tab(draft.id(), List.of()));
        }
        container.setTabs(tabs);
        if (!tabs.isEmpty()) {
            net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts.changed();
        }
        return container;
    }

    private static void reportExternalFiles(@Nullable CompoundTag source, String npcKey,
                                            NpcImportReport report) {
        if (source == null) {
            return;
        }
        ListTag scripts = source.getList(PlacedNpcProfileMigrator.KEY_SCRIPTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < scripts.size(); i++) {
            ListTag files = scripts.getCompound(i).getList("ScriptList", Tag.TAG_STRING);
            for (int j = 0; j < files.size(); j++) {
                report.noteGrouped("script tab " + (i + 1) + " loaded file " + files.getString(j)
                        + " from the CustomNPCs scripts folder; add it to the Xeno script library and"
                        + " load it into that tab", npcKey);
            }
        }
    }

    /**
     * Removes the CNPC script keys this import consumed from a live tag.
     *
     * <p>Safe because {@code PlacedNpcProfileMigrator} keeps the untouched source snapshot under
     * {@code NeoForgeData/XenoPixelsSource}, and because leaving them would have the converted NPC
     * carrying a second, dead copy of its own script - the same argument for why the faction id and
     * dialog options are removed after they are mapped.
     */
    public static void consume(@Nullable CompoundTag tag) {
        if (tag == null) {
            return;
        }
        tag.remove(PlacedNpcProfileMigrator.KEY_SCRIPTS);
        tag.remove(PlacedNpcProfileMigrator.KEY_SCRIPT_LANGUAGE);
        tag.remove(PlacedNpcProfileMigrator.KEY_SCRIPT_ENABLED);
    }

    /** The source language, lower-cased and bounded, with a note when nothing can run it. */
    private static String language(@Nullable CompoundTag source, String npcKey,
                                   NpcImportReport report) {
        String raw = source == null ? "" : source.getString(PlacedNpcProfileMigrator.KEY_SCRIPT_LANGUAGE);
        String language = raw.trim().toLowerCase(Locale.ROOT);
        if (language.isEmpty()) {
            return XenoNpcScripts.DEFAULT_LANGUAGE;
        }
        if (language.length() > XenoNpcScripts.MAX_LANGUAGE_CHARS) {
            report.noteGrouped("script language name was longer than the store allows and fell back to "
                    + XenoNpcScripts.DEFAULT_LANGUAGE, npcKey);
            return XenoNpcScripts.DEFAULT_LANGUAGE;
        }
        if (!RUNNABLE_LANGUAGES.contains(language)) {
            report.noteGrouped("script language " + language
                    + " has no engine this mod bundles; imported as text and stored as written", npcKey);
        }
        return language;
    }

    /** Display name for a stored script, taken from the NPC's own name when it has one. */
    private static String scriptLabel(CompoundTag source, String npcKey) {
        String name = source.getString("Name");
        if (name.isBlank()) {
            name = source.getString("CustomName");
        }
        if (name.isBlank()) {
            // No display name to work with: the id is the traceable thing, so label with it.
            return "NPC " + npcKey;
        }
        return bounded(name.trim(), XenoNpcScripts.MAX_NAME_CHARS - 16) + " script";
    }

    private static String bounded(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() <= max) {
            return result;
        }
        return result.substring(0, Math.max(1, max));
    }
}
