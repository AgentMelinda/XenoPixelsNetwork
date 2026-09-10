package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Selects generated XenoPixels chrome while a normal DragonMineZ V-menu renders. */
public final class DmzMenuThemeState {

    private static final String MENU_BIG = "textures/gui/menu/menubig.png";
    private static final String MENU_SMALL = "textures/gui/menu/menusmall.png";
    private static final String QUEST_MENU = "textures/gui/menu/questmenu.png";
    /**
     * The icon sheets. Unlike the panels these are not split left and right -- every menu draws the
     * same nav row and the same stat buttons -- so they map by path alone, on any themed screen.
     */
    private static final String MENU_BUTTONS = "textures/gui/buttons/menubuttons.png";
    private static final String CHARACTER_BUTTONS = "textures/gui/buttons/characterbuttons.png";
    private static final ThreadLocal<Theme> ACTIVE = new ThreadLocal<>();
    /**
     * Whether any themed menu is mid-render.
     *
     * <p>{@link #remap} is reached from the one blit every other overload funnels into, so it runs
     * for every textured draw in the game. While the rework is parked -- which is the default -- a
     * plain field read is all that costs, instead of a thread-local lookup per draw.
     */
    private static volatile boolean anyActive;

    private DmzMenuThemeState() {
    }

    public static void begin(Object screen) {
        // /xenohud menus stock leaves DMZ's own menus completely alone: nothing is marked active,
        // so remap returns every texture untouched and the multiplier column stays out of the way.
        if (!XenoHudConfig.dmzMenusThemed()) {
            ACTIVE.remove();
            anyActive = false;
            return;
        }
        Theme theme = Theme.fromClassName(screen == null ? "" : screen.getClass().getName());
        ACTIVE.set(theme);
        anyActive = theme != null;
    }

    /**
     * True while a DragonMineZ menu is being drawn with Xeno chrome.
     *
     * <p>Asked by the pieces that redraw DMZ's own content rather than its textures -- the stats
     * multiplier column -- so they change nothing in stock mode, and nothing on a screen this theme
     * does not recognise.
     */
    public static boolean isThemed() {
        return anyActive && ACTIVE.get() != null;
    }

    public static void end() {
        ACTIVE.remove();
        anyActive = false;
    }

    public static ResourceLocation remap(ResourceLocation original, int drawX) {
        if (!anyActive) {
            return original;
        }
        Theme theme = ACTIVE.get();
        if (theme == null || original == null || !"dragonminez".equals(original.getNamespace())) {
            return original;
        }

        String path = original.getPath();
        if (MENU_BUTTONS.equals(path)) {
            return themedOrOriginal("menubuttons.png", original);
        }
        if (CHARACTER_BUTTONS.equals(path)) {
            return themedOrOriginal("characterbuttons.png", original);
        }
        if (QUEST_MENU.equals(path) && theme == Theme.QUESTS) {
            return themedOrOriginal("quests.png", original);
        }
        if (MENU_SMALL.equals(path)) {
            return theme.top == null ? original : themedOrOriginal(theme.top, original);
        }
        if (!MENU_BIG.equals(path) || theme.left == null || theme.right == null) {
            return original;
        }

        return themedOrOriginal(drawX < 160 ? theme.left : theme.right, original);
    }

    private static ResourceLocation themedOrOriginal(String filename, ResourceLocation original) {
        ResourceLocation themed = ResourceLocation.fromNamespaceAndPath(
                "xenopixelsmod", "textures/gui/dmz_menus/" + filename);
        return Minecraft.getInstance().getResourceManager().getResource(themed).isPresent()
                ? themed
                : original;
    }

    private enum Theme {
        CHARACTER("character_left.png", "character_right.png", "character_top.png"),
        SKILLS("skills_left.png", "skills_right.png", "skills_top.png"),
        QUESTS(null, null, null),
        MINIGAMES("minigames_left.png", "minigames_right.png", null),
        PARTY("party_left.png", "party_right.png", null),
        SETTINGS("settings_left.png", "settings_right.png", null);

        private final String left;
        private final String right;
        private final String top;

        Theme(String left, String right, String top) {
            this.left = left;
            this.right = right;
            this.top = top;
        }

        private static Theme fromClassName(String className) {
            return switch (className) {
                case "com.dragonminez.client.gui.character.CharacterStatsScreen" -> CHARACTER;
                case "com.dragonminez.client.gui.character.SkillsMenuScreen" -> SKILLS;
                case "com.dragonminez.client.gui.character.QuestTreeScreen" -> QUESTS;
                case "com.dragonminez.client.gui.character.MinigamesScreen" -> MINIGAMES;
                case "com.dragonminez.client.gui.character.PartyMenuScreen" -> PARTY;
                case "com.dragonminez.client.gui.character.ConfigMenuScreen" -> SETTINGS;
                default -> null;
            };
        }
    }
}
