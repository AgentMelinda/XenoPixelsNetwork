package net.bullettrain.xenopixelsmod.client.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StudioAnimLookupTest {

    @Test
    void studioClipWinsOverShippedForCombatXenoNames() {
        Object studio = new Object();
        Object gecko = new Object();
        assertSame(studio, StudioAnimLookup.preferStudio("combat.xeno_hakai_hold", studio, gecko));
        assertSame(studio, StudioAnimLookup.preferStudio("combat.xeno_hook_left_v4", studio, gecko));
    }

    @Test
    void missingStudioLeavesGeckoResult() {
        Object gecko = new Object();
        assertSame(gecko, StudioAnimLookup.preferStudio("combat.xeno_hakai_hold", null, gecko));
        assertNull(StudioAnimLookup.preferStudio("combat.xeno_hakai_hold", null, null));
    }

    @Test
    void nonCombatNamesNeverPreferStudio() {
        Object studio = new Object();
        Object gecko = new Object();
        assertSame(gecko, StudioAnimLookup.preferStudio("base.block", studio, gecko));
        assertEquals("shipped", StudioAnimLookup.preferStudio(null, "studio", "shipped"));
    }
}
