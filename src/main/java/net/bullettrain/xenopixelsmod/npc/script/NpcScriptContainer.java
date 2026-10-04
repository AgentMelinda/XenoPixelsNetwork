package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * An NPC's script tabs, as CustomNPCs' {@code GuiScriptInterface} lays them out: tabs 1..n, each
 * one script with its own globals, plus the scripts loaded into it through "Load Scripts", and one
 * language and one enabled switch for the whole container.
 *
 * <p>Every tab and every loaded script is a reference into the world store's {@code scripts}
 * folder, never a copy, so the text is edited in one place and a tab id is checked like any other
 * store id. Stored on the NPC's profile under {@link #TAG_TABS}; an NPC saved before tabs existed
 * carries only {@code ScriptId}, which {@link #fromLegacy} turns into tab 1.
 */
public final class NpcScriptContainer {
    public static final String TAG_TABS = "ScriptTabs";
    public static final String TAG_ENABLED = "ScriptsEnabled";
    public static final String TAG_LANGUAGE = "ScriptLanguage";

    /** CustomNPCs caps a container at 40 tabs; the "+" tab disappears at the cap. */
    public static final int MAX_TABS = 40;
    /** Scripts one tab may load ahead of its own text. */
    public static final int MAX_LOADED = 16;

    /** One tab: its own script, and the library scripts evaluated before it. */
    public record Tab(String scriptId, List<String> loaded) {
        public Tab {
            scriptId = scriptId == null ? "" : scriptId.trim();
            loaded = loaded == null ? List.of() : List.copyOf(loaded);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", scriptId);
            if (!loaded.isEmpty()) {
                ListTag list = new ListTag();
                for (String id : loaded) {
                    list.add(StringTag.valueOf(id));
                }
                tag.put("Loaded", list);
            }
            return tag;
        }

        static Tab load(CompoundTag tag) {
            List<String> loaded = new ArrayList<>();
            ListTag list = tag.getList("Loaded", Tag.TAG_STRING);
            for (int i = 0; i < list.size() && loaded.size() < MAX_LOADED; i++) {
                String id = list.getString(i).trim();
                if (!id.isEmpty() && !loaded.contains(id)) {
                    loaded.add(id);
                }
            }
            return new Tab(tag.getString("Id"), loaded);
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private boolean enabled = true;
    private String language = "ecmascript";

    public List<Tab> tabs() {
        return List.copyOf(tabs);
    }

    public boolean enabled() {
        return enabled;
    }

    public String language() {
        return language;
    }

    public boolean isEmpty() {
        return tabs.isEmpty();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLanguage(String language) {
        this.language = language == null || language.isBlank() ? "ecmascript" : language.trim();
    }

    /** Replaces every tab; entries past {@link #MAX_TABS} are dropped. */
    public void setTabs(List<Tab> newTabs) {
        tabs.clear();
        if (newTabs != null) {
            for (Tab tab : newTabs) {
                if (tabs.size() >= MAX_TABS) break;
                if (tab != null) tabs.add(tab);
            }
        }
    }

    /** The first tab's script id, which is what the legacy {@code ScriptId} key still carries. */
    public String firstScriptId() {
        return tabs.isEmpty() ? "" : tabs.get(0).scriptId();
    }

    /** Every store id this container reads, loaded scripts first within each tab. */
    public List<String> referencedIds() {
        List<String> out = new ArrayList<>();
        for (Tab tab : tabs) {
            out.addAll(tab.loaded());
            if (!tab.scriptId().isEmpty()) out.add(tab.scriptId());
        }
        return out;
    }

    public void write(CompoundTag tag) {
        if (tabs.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (Tab tab : tabs) {
            list.add(tab.save());
        }
        tag.put(TAG_TABS, list);
        tag.putBoolean(TAG_ENABLED, enabled);
        tag.putString(TAG_LANGUAGE, language);
    }

    public static NpcScriptContainer read(CompoundTag tag) {
        NpcScriptContainer container = new NpcScriptContainer();
        if (tag == null) {
            return container;
        }
        if (tag.contains(TAG_TABS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_TABS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && container.tabs.size() < MAX_TABS; i++) {
                container.tabs.add(Tab.load(list.getCompound(i)));
            }
            container.enabled = !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
            container.setLanguage(tag.getString(TAG_LANGUAGE));
            return container;
        }
        return fromLegacy(tag.getString("ScriptId"));
    }

    /** A single-script NPC from before tabs: its one script becomes tab 1. */
    public static NpcScriptContainer fromLegacy(@Nullable String scriptId) {
        NpcScriptContainer container = new NpcScriptContainer();
        if (scriptId != null && !scriptId.isBlank()) {
            container.tabs.add(new Tab(scriptId, List.of()));
        }
        return container;
    }

    /**
     * Server-side check for a container arriving from the editor.
     *
     * @return null when acceptable, otherwise the reason
     */
    @Nullable
    public static String reject(CompoundTag tag) {
        if (!tag.contains(TAG_TABS, Tag.TAG_LIST)) {
            return "wrong tag type for " + TAG_TABS;
        }
        ListTag list = tag.getList(TAG_TABS, Tag.TAG_COMPOUND);
        if (list.size() > MAX_TABS) {
            return "more than " + MAX_TABS + " script tabs";
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("Id");
            if (!id.isEmpty() && XenoNpcStorePaths.reject(id) != null) {
                return "invalid script id in tab " + (i + 1);
            }
            ListTag loaded = entry.getList("Loaded", Tag.TAG_STRING);
            if (loaded.size() > MAX_LOADED) {
                return "tab " + (i + 1) + " loads more than " + MAX_LOADED + " scripts";
            }
            for (int j = 0; j < loaded.size(); j++) {
                if (XenoNpcStorePaths.reject(loaded.getString(j)) != null) {
                    return "invalid loaded script id in tab " + (i + 1);
                }
            }
        }
        if (tag.contains(TAG_LANGUAGE) && tag.getString(TAG_LANGUAGE).length() > 32) {
            return "script language name too long";
        }
        return null;
    }
}
