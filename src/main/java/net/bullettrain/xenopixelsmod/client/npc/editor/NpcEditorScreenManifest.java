package net.bullettrain.xenopixelsmod.client.npc.editor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NpcEditorScreenManifest {
    public enum ScreenId {
        DISPLAY,
        STATS,
        AI,
        INVENTORY,
        ADVANCED,
        GLOBAL,
        GLOBAL_BANKS,
        GLOBAL_FACTIONS,
        GLOBAL_FACTION_EDITOR,
        GLOBAL_DIALOGS,
        GLOBAL_DIALOG_NAME,
        GLOBAL_DIALOG_LINES,
        GLOBAL_QUESTS,
        GLOBAL_TRANSPORT,
        GLOBAL_PLAYER_DATA,
        GLOBAL_RECIPES,
        GLOBAL_NATURAL_SPAWNS,
        GLOBAL_LINKED,
        STATS_RESPAWN,
        STATS_MELEE,
        STATS_RANGED,
        STATS_PROJECTILE,
        STATS_RESISTANCE,
        ADVANCED_DIALOG_PICK,
        ADVANCED_DIALOG_OWN,
        ADVANCED_BUBBLE_LINES,
        ADVANCED_TRANSPORT,
        ADVANCED_TRADER,
        ADVANCED_BANK,
        ADVANCED_PATH,
        ADVANCED_GUARD,
        ADVANCED_BARD,
        ADVANCED_HEALER,
        ADVANCED_FOLLOWER,
        ADVANCED_ITEM_GIVER,
        ADVANCED_ITEM_GIVER_AVAILABILITY,
        ADVANCED_FACTIONS,
        ADVANCED_LINES,
        ADVANCED_LINE_SELECTOR,
        ADVANCED_DIALOGS,
        ADVANCED_DIALOG_NODE,
        ADVANCED_DIALOG_OPTION,
        ADVANCED_SOUNDS,
        ADVANCED_NIGHT,
        ADVANCED_LINKED,
        ADVANCED_SCENES,
        ADVANCED_MARKS,
        DMZ,
        DMZ_SKILLS,
        DMZ_TECHNIQUES,
        DMZ_FORMS,
        DMZ_APPEARANCE,
        DMZ_ATTACKS,
        BRAIN,
        DELETE
    }

    public record Page(ScreenId id, String title, ScreenId parent, List<Integer> screenshots,
                       List<String> entries, boolean preview) {
        public Page {
            screenshots = List.copyOf(screenshots);
            entries = List.copyOf(entries);
        }
    }

    private static final List<String> TABS = List.of(
            "Display", "Stats", "AI", "Inventory", "Advanced", "Global", "DMZ", "Brain",
            "Delete", "X");
    private static final Map<ScreenId, Page> PAGES = createPages();

    private NpcEditorScreenManifest() {
    }

    public static List<String> tabs() {
        return TABS;
    }

    public static List<Page> pages() {
        return List.copyOf(PAGES.values());
    }

    public static Page page(ScreenId id) {
        Page page = PAGES.get(id);
        if (page == null) {
            throw new IllegalArgumentException("Unknown NPC editor page " + id);
        }
        return page;
    }

    private static Map<ScreenId, Page> createPages() {
        EnumMap<ScreenId, Page> pages = new EnumMap<>(ScreenId.class);
        add(pages, ScreenId.DISPLAY, "Display", null, List.of(15), List.of(), true);
        add(pages, ScreenId.STATS, "Stats", null, List.of(16), List.of(), true);
        add(pages, ScreenId.AI, "AI", null, List.of(17), List.of(), true);
        add(pages, ScreenId.INVENTORY, "Inventory", null, List.of(18), List.of(), true);
        add(pages, ScreenId.ADVANCED, "Advanced", null, List.of(19, 31, 33), List.of(
                "Lines", "Factions", "Dialogs", "Sounds", "Night", "Linked", "Scenes",
                "Marks"), true);
        add(pages, ScreenId.GLOBAL, "Global", null, List.of(20), List.of(
                "Banks", "Factions", "Dialogs", "Quests", "Transport", "PlayerData",
                "Recipes (no bench)", "Natural Spawns", "Linked"), true);
        add(pages, ScreenId.GLOBAL_BANKS, "Banks", ScreenId.GLOBAL, List.of(21), List.of(), false);
        add(pages, ScreenId.GLOBAL_FACTIONS, "Factions", ScreenId.GLOBAL, List.of(22), List.of(), true);
        add(pages, ScreenId.GLOBAL_FACTION_EDITOR, "Faction", ScreenId.GLOBAL_FACTIONS,
                List.of(23), List.of(), true);
        add(pages, ScreenId.GLOBAL_DIALOGS, "Dialogs", ScreenId.GLOBAL, List.of(24), List.of(), true);
        add(pages, ScreenId.GLOBAL_DIALOG_NAME, "Dialog Name", ScreenId.GLOBAL_DIALOGS,
                List.of(25), List.of(), true);
        add(pages, ScreenId.GLOBAL_DIALOG_LINES, "Dialog Lines", ScreenId.GLOBAL_DIALOG_NAME,
                List.of(), List.of(), true);
        add(pages, ScreenId.GLOBAL_QUESTS, "Quests", ScreenId.GLOBAL, List.of(), List.of(), true);
        add(pages, ScreenId.GLOBAL_TRANSPORT, "Transport", ScreenId.GLOBAL, List.of(26), List.of(), false);
        add(pages, ScreenId.GLOBAL_PLAYER_DATA, "PlayerData", ScreenId.GLOBAL, List.of(27), List.of(), true);
        add(pages, ScreenId.GLOBAL_RECIPES, "Recipes (no bench)", ScreenId.GLOBAL, List.of(28), List.of(), true);
        add(pages, ScreenId.GLOBAL_NATURAL_SPAWNS, "Natural Spawns", ScreenId.GLOBAL,
                List.of(29), List.of(), true);
        add(pages, ScreenId.GLOBAL_LINKED, "Linked", ScreenId.GLOBAL, List.of(30), List.of(), true);
        add(pages, ScreenId.STATS_RESPAWN, "Respawn", ScreenId.STATS, List.of(), List.of(), true);
        add(pages, ScreenId.STATS_MELEE, "Melee Properties", ScreenId.STATS, List.of(), List.of(), true);
        add(pages, ScreenId.STATS_RANGED, "Ranged Properties", ScreenId.STATS, List.of(), List.of(), true);
        add(pages, ScreenId.STATS_PROJECTILE, "Projectile Properties", ScreenId.STATS, List.of(), List.of(), true);
        add(pages, ScreenId.STATS_RESISTANCE, "Resistance", ScreenId.STATS, List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_DIALOG_OWN, "Own Dialogue", ScreenId.ADVANCED_DIALOGS, List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_DIALOG_PICK, "Select Dialog", ScreenId.ADVANCED_DIALOGS, List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_BUBBLE_LINES, "Lines", ScreenId.ADVANCED, List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_TRANSPORT, "Transporter", ScreenId.ADVANCED, List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_TRADER, "Trader", ScreenId.ADVANCED, List.of(32), List.of(), true);
        add(pages, ScreenId.ADVANCED_BANK, "Bank", ScreenId.ADVANCED, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_PATH, "Movement", ScreenId.AI, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_GUARD, "Guard", ScreenId.ADVANCED, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_BARD, "Bard", ScreenId.ADVANCED, List.of(34), List.of(), true);
        add(pages, ScreenId.ADVANCED_HEALER, "Healer", ScreenId.ADVANCED, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_FOLLOWER, "Follower", ScreenId.ADVANCED, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_ITEM_GIVER, "Item Giver", ScreenId.ADVANCED, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_ITEM_GIVER_AVAILABILITY, "Item Giver Availability",
                ScreenId.ADVANCED_ITEM_GIVER, List.of(), List.of(), false);
        add(pages, ScreenId.ADVANCED_FACTIONS, "Factions", ScreenId.ADVANCED, List.of(22), List.of(), true);
        add(pages, ScreenId.ADVANCED_LINES, "Lines", ScreenId.ADVANCED, List.of(35), List.of(
                "World Lines", "Attack Lines", "Interact Lines", "Killed Lines", "Kill Lines",
                "NPC Lines"), true);
        add(pages, ScreenId.ADVANCED_LINE_SELECTOR, "Line Selector", ScreenId.ADVANCED_LINES,
                List.of(36), List.of(), true);
        add(pages, ScreenId.ADVANCED_DIALOGS, "Dialogs", ScreenId.ADVANCED, List.of(37), List.of(), true);
        // No reference screenshot for these two: My NPCs edits a dialogue through its own global
        // registry screens, and this NPC's dialogue is stored on the NPC itself.
        add(pages, ScreenId.ADVANCED_DIALOG_NODE, "Dialog Line", ScreenId.ADVANCED_DIALOGS,
                List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_DIALOG_OPTION, "Dialog Answer", ScreenId.ADVANCED_DIALOG_NODE,
                List.of(), List.of(), true);
        add(pages, ScreenId.ADVANCED_SOUNDS, "Sounds", ScreenId.ADVANCED, List.of(38), List.of(), true);
        add(pages, ScreenId.ADVANCED_NIGHT, "Night", ScreenId.ADVANCED, List.of(39), List.of(), true);
        add(pages, ScreenId.ADVANCED_LINKED, "Linked", ScreenId.ADVANCED, List.of(40), List.of(), true);
        add(pages, ScreenId.ADVANCED_SCENES, "Scenes", ScreenId.ADVANCED, List.of(41), List.of(), true);
        add(pages, ScreenId.ADVANCED_MARKS, "Marks", ScreenId.ADVANCED, List.of(42, 43, 44), List.of(), true);
        add(pages, ScreenId.DMZ, "DMZ", null, List.of(45), List.of(
                "Skills", "Techniques", "Forms", "Attacks", "Appearance"), true);
        add(pages, ScreenId.DMZ_SKILLS, "Skills", ScreenId.DMZ, List.of(), List.of(), true);
        add(pages, ScreenId.DMZ_TECHNIQUES, "Techniques", ScreenId.DMZ, List.of(), List.of(), true);
        add(pages, ScreenId.DMZ_FORMS, "Forms", ScreenId.DMZ, List.of(46), List.of(), true);
        add(pages, ScreenId.DMZ_APPEARANCE, "Appearance", ScreenId.DMZ, List.of(47), List.of(), true);
        // No reference screenshot: the ordered attack slots are ours, not a My NPCs screen.
        add(pages, ScreenId.DMZ_ATTACKS, "Attacks", ScreenId.DMZ, List.of(), List.of(), true);
        add(pages, ScreenId.BRAIN, "Brain", null, List.of(48), List.of(), true);
        add(pages, ScreenId.DELETE, "Delete", null, List.of(), List.of(), true);
        return Map.copyOf(pages);
    }

    private static void add(Map<ScreenId, Page> pages, ScreenId id, String title,
                            ScreenId parent, List<Integer> screenshots, List<String> entries,
                            boolean preview) {
        pages.put(id, new Page(id, title, parent, screenshots, entries, preview));
    }
}
