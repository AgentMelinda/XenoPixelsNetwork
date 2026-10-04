package net.bullettrain.xenopixelsmod.ui;

import java.util.Locale;

/** Which DragonMineZ V-menu an authored document may replace. */
public enum DmzMenuPage {
    STATS("stats"),
    SKILLS("skills"),
    QUESTS("quests"),
    PARTY("party"),
    OPTIONS("options"),
    CUSTOM("custom");

    private final String id;

    DmzMenuPage(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DmzMenuPage fromDmzScreenClass(String className) {
        if (className == null) return null;
        return switch (className) {
            case "com.dragonminez.client.gui.character.CharacterStatsScreen" -> STATS;
            case "com.dragonminez.client.gui.character.SkillsMenuScreen" -> SKILLS;
            case "com.dragonminez.client.gui.character.QuestTreeScreen" -> QUESTS;
            case "com.dragonminez.client.gui.character.PartyMenuScreen" -> PARTY;
            case "com.dragonminez.client.gui.character.ConfigMenuScreen" -> OPTIONS;
            default -> null;
        };
    }

    public static DmzMenuPage parse(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return null;
        for (DmzMenuPage page : values()) {
            if (page.id.equals(key)) return page;
        }
        return null;
    }
}
