package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.config.DmzMenuMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which sheet each DragonMineZ menu draw is handed, in each mode.
 *
 * <p>Pinned here because this has been wrong in play twice and neither failure was visible from the
 * code: the navigation row stayed stock for a whole release because no draw was ever offered for
 * remapping, and the settings page's left panel takes the right panel's art on any window wide
 * enough to matter. Both are arithmetic and table lookup, so both are checkable without a client.
 */
class DmzMenuArtTest {

    private static final Path GENERATED = Path.of(
            "src/main/resources/assets/xenopixelsmod");

    @Test
    void everyScreenClassMapsToItsOwnPage() {
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            assertEquals(page, DmzMenuArt.pageOf(page.screenClass()),
                    page + " no longer maps from its own screen class");
        }
    }

    @Test
    void unknownScreensAreLeftAlone() {
        assertNull(DmzMenuArt.pageOf(null));
        assertNull(DmzMenuArt.pageOf("net.minecraft.client.gui.screens.TitleScreen"));
        // A near miss: DragonMineZ has other screens in the same package and none of them is a V
        // menu. Theming one of those would dress a screen this mod has no art for.
        assertNull(DmzMenuArt.pageOf(
                "com.dragonminez.client.gui.character.CharacterCustomizationScreen"));
    }

    @Test
    void stockModeThemesNothing() {
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            assertArrayEquals(new String[0],
                    DmzMenuArt.sheets(page, DmzMenuMode.STOCK, DmzMenuArt.MENU_BIG, 12, 320),
                    page + " was themed in stock mode");
        }
    }

    @Test
    void neonPrefersItsOwnSheetAndFallsBackToTheme() {
        String[] sheets = DmzMenuArt.sheets(DmzMenuArt.Page.SETTINGS, DmzMenuMode.NEON,
                DmzMenuArt.MENU_BIG, 12, 320);
        assertEquals(2, sheets.length, "neon should offer its own sheet and the theme sheet");
        assertEquals(DmzMenuArt.NEON_DIRECTORY + "settings_left.png", sheets[0]);
        assertEquals(DmzMenuArt.DIRECTORY + "settings_left.png", sheets[1]);
    }

    @Test
    void themeModeNeverReachesForNeonArt() {
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            for (String path : new String[]{DmzMenuArt.MENU_BIG, DmzMenuArt.MENU_SMALL,
                    DmzMenuArt.QUEST_MENU, DmzMenuArt.MENU_BUTTONS,
                    DmzMenuArt.CHARACTER_BUTTONS}) {
                for (String sheet : DmzMenuArt.sheets(page, DmzMenuMode.THEME, path, 12, 320)) {
                    assertFalse(sheet.startsWith(DmzMenuArt.NEON_DIRECTORY),
                            "theme mode reached for " + sheet);
                }
            }
        }
    }

    @Test
    void widgetSheetsArePerPageInNeonAndSharedInTheme() {
        List<String> seen = new ArrayList<>();
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            String[] neon = DmzMenuArt.sheets(page, DmzMenuMode.NEON,
                    DmzMenuArt.CHARACTER_BUTTONS, 12, 320);
            assertEquals(DmzMenuArt.NEON_DIRECTORY + page.id() + "_characterbuttons.png", neon[0]);
            assertFalse(seen.contains(neon[0]), page + " shares a widget sheet with another page");
            seen.add(neon[0]);

            assertArrayEquals(new String[]{DmzMenuArt.DIRECTORY + "characterbuttons.png"},
                    DmzMenuArt.sheets(page, DmzMenuMode.THEME, DmzMenuArt.CHARACTER_BUTTONS,
                            12, 320));
        }
    }

    @Test
    void onlyTheQuestTreeGetsTheQuestSheet() {
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            String[] sheets = DmzMenuArt.sheets(page, DmzMenuMode.NEON, DmzMenuArt.QUEST_MENU,
                    0, 320);
            if (page == DmzMenuArt.Page.QUESTS) {
                assertEquals(DmzMenuArt.NEON_DIRECTORY + "quests.png", sheets[0]);
            } else {
                assertArrayEquals(new String[0], sheets,
                        page + " was handed the quest tree's sheet");
            }
        }
    }

    /**
     * The bug this whole split exists for.
     *
     * <p>{@code ConfigMenuScreen} draws its left panel at {@code uiWidth / 2 - 143} and its right at
     * {@code uiWidth / 2 + 2}; the other four draw theirs at 12 and {@code uiWidth - 158}. Both
     * shapes have to land on the correct side at every canvas size, not only at the 320 minimum.
     */
    @Test
    void neonSplitsOnTheRealMidpointAtEveryCanvasWidth() {
        for (int uiWidth : new int[]{320, 480, 640, 900, 1280}) {
            assertLeft(uiWidth, uiWidth / 2 - 143, "settings left panel");
            assertLeft(uiWidth, uiWidth / 2 - 126, "settings left header");
            assertRight(uiWidth, uiWidth / 2 + 2, "settings right panel");
            assertRight(uiWidth, uiWidth / 2 + 19, "settings right header");

            assertLeft(uiWidth, 12, "shared left panel");
            assertLeft(uiWidth, 29, "shared left header");
            assertRight(uiWidth, uiWidth - 158, "shared right panel");
            assertRight(uiWidth, uiWidth - 141, "shared right header");
            // PartyMenuScreen's extra sub-header, at rightX + 31.
            assertRight(uiWidth, uiWidth - 127, "party sub-header");
        }
    }

    @Test
    void themeKeepsItsLegacyThresholdEvenWhereItIsWrong() {
        // Not an endorsement: THEME is a separately selectable rework whose look has been tuned
        // against this threshold, so it is left exactly as it was and only NEON is corrected.
        assertTrue(DmzMenuArt.drawsLeftPanel(DmzMenuMode.THEME, 159, 640));
        assertFalse(DmzMenuArt.drawsLeftPanel(DmzMenuMode.THEME, 177, 640));
        // ... and that 177 is the settings page's left panel at a 640 canvas, which is the failure.
        assertTrue(DmzMenuArt.drawsLeftPanel(DmzMenuMode.NEON, 177, 640));
    }

    @Test
    void anUnreadableCanvasWidthFallsBackToTheLegacyThreshold() {
        // uiWidth is 0 when the invoker could not read it. A midpoint of 0 would put every draw on
        // the right, so the constant has to win instead.
        assertTrue(DmzMenuArt.drawsLeftPanel(DmzMenuMode.NEON, 12, 0));
        assertFalse(DmzMenuArt.drawsLeftPanel(DmzMenuMode.NEON, 162, 0));
    }

    /**
     * Every sheet the mapping can name is a file that exists.
     *
     * <p>A name that resolves to nothing degrades silently to DragonMineZ's own art at runtime, so
     * without this a page could quietly stop being themed and look merely "not done yet".
     */
    @Test
    void everySheetTheMappingNamesWasGenerated() {
        if (!Files.isDirectory(GENERATED)) {
            return;
        }
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            for (DmzMenuMode mode : new DmzMenuMode[]{DmzMenuMode.THEME, DmzMenuMode.NEON}) {
                for (String path : new String[]{DmzMenuArt.MENU_BIG, DmzMenuArt.MENU_SMALL,
                        DmzMenuArt.QUEST_MENU, DmzMenuArt.MENU_BUTTONS,
                        DmzMenuArt.CHARACTER_BUTTONS}) {
                    for (int drawX : new int[]{12, 300}) {
                        for (String sheet : DmzMenuArt.sheets(page, mode, path, drawX, 640)) {
                            assertTrue(Files.isRegularFile(GENERATED.resolve(sheet)),
                                    "missing generated sheet " + sheet + " for " + page
                                            + " in " + mode + " mode");
                        }
                    }
                }
            }
        }
    }

    /** The six pages NEON claims to dress all have panel art of their own, except the one it replaces. */
    @Test
    void neonHasItsOwnPanelArtForEveryThemedPage() {
        if (!Files.isDirectory(GENERATED)) {
            return;
        }
        for (DmzMenuArt.Page page : DmzMenuArt.Page.values()) {
            String own = page == DmzMenuArt.Page.QUESTS
                    ? DmzMenuArt.NEON_DIRECTORY + "quests.png"
                    : DmzMenuArt.NEON_DIRECTORY + page.id() + "_left.png";
            boolean present = Files.isRegularFile(GENERATED.resolve(own));
            if (page == DmzMenuArt.Page.CHARACTER) {
                // NEON replaces this screen outright with XenoNeonStatsScreen rather than theming
                // it, so it deliberately has no neon panel sheet and keeps the THEME one.
                assertFalse(present, "the character page should not have neon panel art");
                assertNotNull(DmzMenuArt.sheets(page, DmzMenuMode.NEON, DmzMenuArt.MENU_BIG,
                        12, 640)[1]);
            } else {
                assertTrue(present, page + " has no neon panel art");
            }
        }
    }

    private static void assertLeft(int uiWidth, int drawX, String what) {
        assertTrue(DmzMenuArt.drawsLeftPanel(DmzMenuMode.NEON, drawX, uiWidth),
                what + " at x=" + drawX + " read as the right panel at uiWidth=" + uiWidth);
    }

    private static void assertRight(int uiWidth, int drawX, String what) {
        assertFalse(DmzMenuArt.drawsLeftPanel(DmzMenuMode.NEON, drawX, uiWidth),
                what + " at x=" + drawX + " read as the left panel at uiWidth=" + uiWidth);
    }
}
