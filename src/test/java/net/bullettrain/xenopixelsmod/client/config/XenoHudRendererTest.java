package net.bullettrain.xenopixelsmod.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The renderer id mapping, and the migration off the two old booleans.
 *
 * <p>The migration is the part that matters: an existing player's HUD has to look identical after
 * the upgrade, and getting it wrong silently swaps their renderer rather than failing.
 */
class XenoHudRendererTest {

    @Test
    void everyBooleanCombinationMapsToTheRendererItUsedToMean() {
        assertEquals(XenoHudRenderer.LEGACY, XenoHudRenderer.fromLegacyFlags(true, false));
        assertEquals(XenoHudRenderer.LEGACY, XenoHudRenderer.fromLegacyFlags(true, true));
        assertEquals(XenoHudRenderer.MODERN, XenoHudRenderer.fromLegacyFlags(false, false));
        assertEquals(XenoHudRenderer.MODERN_UNIFIED, XenoHudRenderer.fromLegacyFlags(false, true));
    }

    @Test
    void migrationNeverLandsOnBt3() {
        // bt3 is opt-in. No combination of the old flags may select it.
        for (boolean legacy : new boolean[]{false, true}) {
            for (boolean unified : new boolean[]{false, true}) {
                assertEquals(false,
                        XenoHudRenderer.fromLegacyFlags(legacy, unified) == XenoHudRenderer.BT3,
                        "legacy=" + legacy + " unified=" + unified + " must not migrate to bt3");
            }
        }
    }

    @Test
    void everyIdParsesBackToItsOwnRenderer() {
        for (XenoHudRenderer renderer : XenoHudRenderer.values()) {
            assertEquals(renderer, XenoHudRenderer.parse(renderer.id(), XenoHudRenderer.LEGACY));
        }
    }

    @Test
    void ldlibStillResolvesToModern() {
        // Existing macros and muscle memory must keep working.
        assertEquals(XenoHudRenderer.MODERN, XenoHudRenderer.parse("ldlib", XenoHudRenderer.LEGACY));
        assertEquals(XenoHudRenderer.MODERN, XenoHudRenderer.parse("  LDLib ", XenoHudRenderer.LEGACY));
    }

    @Test
    void unknownInputFallsBackInsteadOfLeavingNoRenderer() {
        assertEquals(XenoHudRenderer.MODERN_UNIFIED,
                XenoHudRenderer.parse(null, XenoHudRenderer.MODERN_UNIFIED));
        assertEquals(XenoHudRenderer.MODERN_UNIFIED,
                XenoHudRenderer.parse("", XenoHudRenderer.MODERN_UNIFIED));
        assertEquals(XenoHudRenderer.MODERN_UNIFIED,
                XenoHudRenderer.parse("nonsense", XenoHudRenderer.MODERN_UNIFIED));
    }

    @Test
    void usageListsEveryRenderer() {
        String usage = XenoHudRenderer.usage();
        assertNotNull(usage);
        for (XenoHudRenderer renderer : XenoHudRenderer.values()) {
            assertEquals(true, usage.contains(renderer.id()), "usage missing " + renderer.id());
        }
    }
}
