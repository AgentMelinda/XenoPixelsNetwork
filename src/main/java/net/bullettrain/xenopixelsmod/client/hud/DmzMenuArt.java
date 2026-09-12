package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;

/**
 * Which Xeno texture stands in for which DragonMineZ menu texture, and on which page.
 *
 * <p>Split out of {@link DmzMenuThemeState} so the decision itself holds no Minecraft types. What
 * this answers -- is this screen themed, is this draw the left panel or the right one, which file
 * does this page want -- is arithmetic and table lookup, and it is the part that has actually been
 * wrong in play, so it is the part that has to be testable without a client.
 *
 * <p>{@link DmzMenuThemeState} keeps everything this cannot do without the game: asking the resource
 * manager whether a generated sheet is really present, and falling back when it is not.
 */
public final class DmzMenuArt {

    /** DragonMineZ's own texture paths, in the {@code dragonminez} namespace. */
    public static final String MENU_BIG = "textures/gui/menu/menubig.png";
    public static final String MENU_SMALL = "textures/gui/menu/menusmall.png";
    public static final String QUEST_MENU = "textures/gui/menu/questmenu.png";
    public static final String MENU_BUTTONS = "textures/gui/buttons/menubuttons.png";
    public static final String CHARACTER_BUTTONS = "textures/gui/buttons/characterbuttons.png";

    /** Where the generated sheets live, under {@code assets/xenopixelsmod/}. */
    public static final String DIRECTORY = "textures/gui/dmz_menus/";
    public static final String NEON_DIRECTORY = DIRECTORY + "neon/";

    /**
     * The midpoint {@link DmzMenuMode#THEME} splits the two panels on.
     *
     * <p>Half of {@code ScaledScreen}'s 320 minimum canvas, and correct only while the canvas is
     * exactly that minimum. It is kept because THEME is a shipped, separately selectable rework
     * whose look people have already tuned against; {@link DmzMenuMode#NEON} uses the real midpoint
     * instead -- see {@link #drawsLeftPanel}.
     */
    public static final int LEGACY_SPLIT_X = 160;

    private DmzMenuArt() {
    }

    /** One of DragonMineZ's six V-menus, with the sheets each mode has generated for it. */
    public enum Page {
        CHARACTER("com.dragonminez.client.gui.character.CharacterStatsScreen",
                "character_left.png", "character_right.png", "character_top.png"),
        SKILLS("com.dragonminez.client.gui.character.SkillsMenuScreen",
                "skills_left.png", "skills_right.png", "skills_top.png"),
        // THEME never generated panel sheets for the quest tree: that screen samples questmenu.png
        // and nothing else, which is handled by the quest branch of `sheet` rather than by a side.
        QUESTS("com.dragonminez.client.gui.character.QuestTreeScreen", null, null, null),
        MINIGAMES("com.dragonminez.client.gui.character.MinigamesScreen",
                "minigames_left.png", "minigames_right.png", null),
        PARTY("com.dragonminez.client.gui.character.PartyMenuScreen",
                "party_left.png", "party_right.png", null),
        SETTINGS("com.dragonminez.client.gui.character.ConfigMenuScreen",
                "settings_left.png", "settings_right.png", null);

        private final String screenClass;
        private final String left;
        private final String right;
        private final String top;

        Page(String screenClass, String left, String right, String top) {
            this.screenClass = screenClass;
            this.left = left;
            this.right = right;
            this.top = top;
        }

        public String screenClass() {
            return screenClass;
        }

        /** The page's own id, which is also the prefix its generated sheets are named with. */
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final Page[] PAGES = Page.values();

    /** The page a screen class name names, or null for any other screen. */
    public static Page pageOf(String className) {
        if (className == null) {
            return null;
        }
        for (Page page : PAGES) {
            if (page.screenClass.equals(className)) {
                return page;
            }
        }
        return null;
    }

    /**
     * Whether a draw at {@code drawX} is the left slab rather than the right one.
     *
     * <p>The texture hook sees a draw's x coordinate and nothing else, so the side has to be
     * recovered from it. Every menu but the settings page puts its left slab at x=12 and its right
     * at {@code uiWidth - 158}; the settings page puts them at {@code uiWidth / 2 - 143} and
     * {@code uiWidth / 2 + 2}. All five therefore split cleanly on the canvas midpoint, and none of
     * them splits reliably on a constant: at a UI width of 640 the settings page's left panel is
     * drawn at x=177, and the old 160 threshold hands it the right panel's art.
     *
     * @param uiWidth DragonMineZ's own logical canvas width, or a value below
     *                {@code 2 * LEGACY_SPLIT_X} when it could not be read -- in which case the
     *                legacy threshold is used, because a midpoint guessed from a bad width would be
     *                worse than the constant it replaces
     */
    public static boolean drawsLeftPanel(DmzMenuMode mode, int drawX, int uiWidth) {
        if (mode != DmzMenuMode.NEON || uiWidth < LEGACY_SPLIT_X * 2) {
            return drawX < LEGACY_SPLIT_X;
        }
        return drawX < uiWidth / 2;
    }

    /**
     * The Xeno sheets that may stand in for one DragonMineZ texture, best first.
     *
     * <p>More than one because {@link DmzMenuMode#NEON} is an addition to {@link DmzMenuMode#THEME}
     * rather than a replacement for it: a page NEON has art for uses it, and a page it does not --
     * the character screen, which NEON replaces outright with {@code XenoNeonStatsScreen} rather
     * than theming -- keeps the THEME sheet instead of falling all the way back to DragonMineZ. The
     * caller drops any candidate that is not actually present.
     *
     * @return paths under {@code assets/xenopixelsmod/}, or an empty array to leave the draw alone
     */
    public static String[] sheets(Page page, DmzMenuMode mode, String dmzPath,
                                  int drawX, int uiWidth) {
        if (page == null || dmzPath == null || !mode.themed()) {
            return NONE;
        }
        boolean neon = mode == DmzMenuMode.NEON;

        // The navigation row is the same six buttons on every page, so it maps by path alone and
        // both modes share one sheet.
        if (MENU_BUTTONS.equals(dmzPath)) {
            return one(DIRECTORY + "menubuttons.png");
        }
        // The widget sheet is per page in NEON -- each pack draws its own action button and arrows
        // -- and shared in THEME, which only ever generated the one.
        if (CHARACTER_BUTTONS.equals(dmzPath)) {
            return neon
                    ? two(NEON_DIRECTORY + page.id() + "_characterbuttons.png",
                          DIRECTORY + "characterbuttons.png")
                    : one(DIRECTORY + "characterbuttons.png");
        }
        if (QUEST_MENU.equals(dmzPath)) {
            // Only the quest tree samples this sheet, and only its own art belongs on it.
            return page != Page.QUESTS ? NONE
                    : neon ? two(NEON_DIRECTORY + "quests.png", DIRECTORY + "quests.png")
                           : one(DIRECTORY + "quests.png");
        }
        if (MENU_SMALL.equals(dmzPath)) {
            String themedTop = page.top == null ? null : DIRECTORY + page.top;
            return neon ? two(NEON_DIRECTORY + page.id() + "_top.png", themedTop)
                        : one(themedTop);
        }
        if (!MENU_BIG.equals(dmzPath)) {
            return NONE;
        }
        boolean left = drawsLeftPanel(mode, drawX, uiWidth);
        String side = left ? page.left : page.right;
        String themed = side == null ? null : DIRECTORY + side;
        return neon
                ? two(NEON_DIRECTORY + page.id() + (left ? "_left.png" : "_right.png"), themed)
                : one(themed);
    }

    private static final String[] NONE = new String[0];

    /** One full path, or nothing when that slot has no generated sheet. */
    private static String[] one(String path) {
        return path == null ? NONE : new String[]{path};
    }

    /** A neon sheet, then the THEME sheet for the same slot if that mode generated one. */
    private static String[] two(String neon, String themed) {
        return themed == null ? new String[]{neon} : new String[]{neon, themed};
    }
}
